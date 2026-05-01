// proxy/main.go
package main

import (
	"encoding/binary"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"strings"
	"time"

	utls "github.com/refraction-networking/utls"
	"github.com/DumPr0jects/Ai-zapret-mobile/proxy/obfuscate"
	"github.com/DumPr0jects/Ai-zapret-mobile/proxy/strategies"
)

const (
	PROXY_ADDR = "127.0.0.1:1080"
	VERSION    = "2.1.0"
)

var (
	strategyFlag = flag.String("strategy", "mixed", "DPI bypass strategy: none, split-sni, fake-tls, padding, mixed")
	logFlag      = flag.Bool("log", false, "Enable verbose logging")
	portFlag     = flag.String("port", "1080", "SOCKS5 proxy port")
)

var clientIDs = []utls.ClientHelloID{
	utls.HelloChrome_83, utls.HelloChrome_70,
	utls.HelloFirefox_63, utls.HelloRandomizedALPN,
}

func main() {
	flag.Parse()
	addr := fmt.Sprintf("127.0.0.1:%s", *portFlag)
	
	listener, err := net.Listen("tcp", addr)
	if err != nil {
		log.Fatalf("🔴 Listen failed: %v", err)
	}

	log.Printf("🟢 DPI Proxy v%s [%s] on %s", VERSION, *strategyFlag, addr)
	if !*logFlag { log.SetOutput(io.Discard) }

	for {
		conn, err := listener.Accept()
		if err != nil { continue }
		go handleClient(conn, *strategyFlag)
	}
}

func handleClient(client net.Conn, strategy string) {
	defer client.Close()

	// SOCKS5 Handshake
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
	} else {
		client.Write([]byte{5, 0x08, 0, 0, 0, 0, 0, 0}); return
	}
	
	port := make([]byte, 2); io.ReadFull(client, port)
	destAddr := fmt.Sprintf("%s:%d", addr, binary.BigEndian.Uint16(port))
	client.Write([]byte{5, 0, 0, 1, 0, 0, 0, 0, 0, 0})

	// Connect with obfuscation
	obfuscate.PreConnectDelay()
	dest, err := dialObfuscated(destAddr, addr)
	if err != nil {
		log.Printf("⚠️ Connect failed to %s: %v", destAddr, err)
		client.Write([]byte{5, 0x05, 0, 1, 0, 0, 0, 0, 0, 0})
		return
	}
	defer dest.Close()

	log.Printf("🔗 Routing %s -> %s [%s]", client.RemoteAddr(), destAddr, strategy)

	// Relay with strategy application
	go relayWithStrategy(client, dest, strategy)
	relayWithStrategy(dest, client, strategy)
}

func dialObfuscated(destAddr, sni string) (net.Conn, error) {
	id := clientIDs[int64(time.Now().UnixNano())%int64(len(clientIDs))]
	cfg := &utls.Config{ServerName: sni}
	
	conn, err := net.DialTimeout("tcp", destAddr, 8*time.Second)
	if err != nil { return nil, err }
	
	uClient := utls.UClient(conn, cfg, id)
	if err := uClient.Handshake(); err != nil {
		conn.Close()
		return nil, err
	}
	return uClient, nil
}

func relayWithStrategy(src, dst net.Conn, strategy string) {
	defer src.Close()
	defer dst.Close()

	buf := make([]byte, 4096)
	n, err := src.Read(buf)
	if err != nil { return }

	data := buf[:n]
	var splitDelay time.Duration

	// Применяем стратегию к первому пакету
	switch strategy {
	case "split-sni":
		data, splitDelay = strategies.SplitSNI(data)
	case "fake-tls":
		data = strategies.FakeTLS(data)
	case "padding":
		data = strategies.Padding(data)
	case "mixed":
		data, splitDelay = strategies.Mixed(data)
	}

	// Отправляем первый пакет
	if _, err := dst.Write(data); err != nil { return }
	
	// Эмуляция split-задержки (если стратегия требует)
	if splitDelay > 0 {
		time.Sleep(splitDelay)
	}

	// Передаём остальные пакеты с джиттером
	for {
		obfuscate.RelayJitter()
		n, err := src.Read(buf)
		if err != nil { return }
		if _, err := dst.Write(buf[:n]); err != nil { return }
	}
}
