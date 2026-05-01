package strategies

import (
	"math/rand"
	"time"
)

// SplitSNI обрабатывает первый TLS-пакет, готовя данные для эмуляции разделения
// В user-space настоящее TCP-сегментирование невозможно без raw sockets,
// поэтому мы возвращаем данные + задержку, которую main.go применит при отправке.
func SplitSNI(data []byte) ([]byte, time.Duration) {
	if len(data) < 6 || data[0] != 0x16 {
		return data, 0
	}
	splitPoint := 3 + rand.Intn(3)
	if splitPoint >= len(data) {
		return data, 0
	}
	// Возвращаем данные как есть, но сигнализируем о необходимости задержки
	// Реальное разделение произойдёт за счёт write() + sleep() в main.go
	return data, time.Duration(5+rand.Intn(15)) * time.Millisecond
}
