// proxy/main.go
package main

import (
	"bufio"
	"bytes"
	"crypto/rand"
	"encoding/binary"
	"flag"
	"fmt"
	"io"
	"log"
	mrand "math/rand"
	"net"
	"os"
	"strings"
	"time"

	utls "github.com/refraction-networking/utls"
)

const (
	PROXY_ADDR = "127.0.0.1:1080"
	VERSION    = "2.0.0"
	TEST_DOMAINS = []string{"dns.google:443", "cloudflare-dns.com:443"}
)

// === Стратегии ===
type Strategy int
const (
	StrNone Strategy = iota
	StrSplitSNI
	StrFakeTLS
	StrTTLObfuscate
	StrPadding
	StrTimingJitter
	StrSNIMask
	StrMixed  // Комбинация
)

var strategyMap = map[string]Strategy{
	"none": StrNone, "split-sni": StrSplitSNI, "fake-tls": StrFakeTLS,
	"ttl": StrTTLObfuscate, "padding": StrPadding, "jitter": StrTimingJitter,
	"sni-mask": StrSNIMask, "mixed": StrMixed,
}

var (
	strategyFlag = flag.String("strategy", "mixed", "DPI bypass strategy")
	logFlag      = flag.Bool("log", false, "Enable verbose logging")
	portFlag     = flag.String("port", "1080", "SOCKS5 port")
	testFlag     = flag.Bool("test", false, "Test connectivity before start")
)

var clientIDs = []utls.ClientHelloID{
	utls.HelloChrome_83, utls.HelloChrome_70,
	utls.HelloFirefox_63, utls.HelloRandomizedALPN,
}

func init() { mrand.Seed(time.Now().UnixNano()) }

func main() {
	flag.Parse()
	strategy := strategyMap[*strategyFlag]
	
	if *testFlag && !testConnectivity() {
		log.Fatalf("❌ Connectivity test failed. Check network/firewall.")
	}

	addr := fmt.Sprintf("127.0.0.1:%s", *portFlag)
	listener, err := net.Listen("tcp", addr)
	if err != nil { log.Fatalf("🔴 Listen failed: %v", err) }

	log.Printf("🟢 DPI Proxy v%s [%s] on %s", VERSION, *strategyFlag, addr)
	if !*logFlag { log.SetOutput(io.Discard) }

	for {
		conn, err := listener.Accept()
		if err != nil { continue }
		go handleClient(conn, strategy)
	}
}

// 🔥 Проверка подключения перед стартом
func testConnectivity() bool {
	for _, domain := range TEST_DOMAINS {
		conn, err := net.DialTimeout("tcp", domain, 3*time.Second)
		if err == nil { conn.Close(); return true }
	}
	return false
}

func handleClient(client net.Conn, strategy Strategy) {
	defer client.Close()

	// SOCKS5 handshake
	buf := make([]byte, 2)
	if _, err := io.ReadFull(client, buf); err != nil || buf[0] != 5 { return }
	client.Write([]byte{5, 0})
	if _, err := io.ReadFull(client, buf); err != nil || buf[0] != 5 || buf[1] != 1 {
		client.Write([]byte{5, 0x07, 0, 0, 0, 0, 0, 0}); return
	}

	// Parse destination
	atype := make([]byte, 1); io.ReadFull(client, atype)
	var addr string
	if atype[0] == 1 {
		ip := make([]byte, 4); io.ReadFull(client, ip)
		addr = fmt.Sprintf("%d.%d.%d.%d", ip[0], ip[1], ip[2], ip[3])
	} else if atype[0] == 3 {
		dlen := make([]byte, 1); io.ReadFull(client, dlen)
		domain := make([]byte, dlen[0]); io.ReadFull(client, domain)
		addr = string(domain)
	} else { client.Write([]byte{5, 0x08, 0, 0, 0, 0, 0, 0}); return }
	
	port := make([]byte, 2); io.ReadFull(client, port)
	destAddr := fmt.Sprintf("%s:%d", addr, binary.BigEndian.Uint16(port))
	client.Write([]byte{5, 0, 0, 1, 0, 0, 0, 0, 0, 0})

	// Connect with strategy + fallback
	dest, err := connectWithFallback(destAddr, addr, strategy)
	if err != nil {
		client.Write([]byte{5, 0x05, 0, 1, 0, 0, 0, 0, 0, 0}); return
	}
	defer dest.Close()

	go relayObfuscated(client, dest, strategy)
	relayObfuscated(dest, client, strategy)
}

// 🔥 Авто-переключение стратегий при неудаче
func connectWithFallback(destAddr, sni string, primary Strategy) (net.Conn, error) {
	strategies := []Strategy{primary}
	if primary != StrMixed { strategies = append(strategies, StrMixed) }
	strategies = append(strategies, StrSplitSNI, StrFakeTLS, StrNone)

	for _, strat := range strategies {
		conn, err := dialObfuscated(destAddr, sni, strat)
		if err == nil {
			log.Printf("🔗 Connected via strategy: %v", strat)
			return conn, nil
		}
		log.Printf("⚠️ Strategy %v failed: %v", strat, err)
	}
	return nil, fmt.Errorf("all strategies failed")
}

func dialObfuscated(destAddr, sni string, strategy Strategy) (net.Conn, error) {
	id := clientIDs[mrand.Int63n(int64(len(clientIDs)))]
	cfg := &utls.Config{ServerName: maskSNI(sni, strategy)}

	applyJitter(strategy)

	conn, err := net.DialTimeout("tcp", destAddr, 8*time.Second)
	if err != nil { return nil, err }

	uConn := utls.UClient(conn, cfg, id)
	if err := uConn.Handshake(); err != nil { conn.Close(); return nil, err }

	return uConn, nil
}

// 🔥 Маскировка SNI
func maskSNI(sni string, strategy Strategy) string {
	if strategy == StrSNIMask || strategy == StrMixed {
		if mrand.Float64() < 0.5 {
			cdns := []string{"cdn.cloudflare.com", "ocsp.digicert.com", "www.microsoft.com"}
			return cdns[mrand.Intn(len(cdns))]
		}
	}
	return sni
}

// 🔥 Timing obfuscation
func applyJitter(strategy Strategy) {
	if strategy == StrTimingJitter || strategy == StrMixed {
		time.Sleep(time.Duration(mrand.Intn(100)+20) * time.Millisecond)
	}
}

// 🔥 Obfuscated relay с поддержкой стратегий
func relayObfuscated(src, dst net.Conn, strategy Strategy) {
	defer src.Close(); defer dst.Close()
	buf := make([]byte, 4096)

	// Первый пакет — применяем обфускацию
	n, err := src.Read(buf)
	if err != nil { return }
	
	data := applyObfuscation(buf[:n], strategy)
	if _, err := dst.Write(data); err != nil { return }

	// Остальные пакеты — с джиттером
	for {
		if strategy == StrTimingJitter || strategy == StrMixed {
			time.Sleep(time.Duration(mrand.Intn(3)+1) * time.Millisecond)
		}
		n, err := src.Read(buf)
		if err != nil { return }
		if _, err := dst.Write(buf[:n]); err != nil { return }
	}
}

// 🔥 Применение обфускации к данным
func applyObfuscation(data []byte, strategy Strategy) []byte {
	if len(data) < 10 { return data }

	switch strategy {
	case StrSplitSNI, StrMixed:
		// Эмуляция split на границе 3-5 байт (где обычно начинается SNI)
		if len(data) > 5 && data[0] == 0x16 { // TLS handshake
			splitPoint := 3 + mrand.Intn(3)
			part1, part2 := data[:splitPoint], data[splitPoint:]
			// В реальности split происходит на уровне сети, здесь — подготовка
			_ = part1; _ = part2
		}
		return data

	case StrFakeTLS, StrMixed:
		// Добавляем фейковую TLS запись перед реальными данными
		if len(data) > 20 && data[0] == 0x16 {
			fake := []byte{0x16, 0x03, 0x01, 0x00, 0x00} // Fake ClientHello header
			fake = append(fake, make([]byte, mrand.Intn(50)+10)...) // Random padding
			return append(fake, data...)
		}
		return data

	case StrPadding, StrMixed:
		if len(data) < 512 {
			padding := make([]byte, mrand.Intn(200)+50)
			rand.Read(padding)
			return append(data, padding...)
		}
		return data

	case StrTTLObfuscate:
		// TTL manipulation требует raw sockets — эмулируем через задержку
		time.Sleep(time.Duration(mrand.Intn(20)+5) * time.Millisecond)
		return data

	default:
		return data
	}
}
