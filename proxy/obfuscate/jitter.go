package obfuscate

import (
	"math/rand"
	"time"
)

// PreConnectDelay добавляет случайную задержку перед handshake
func PreConnectDelay() {
	ms := 20 + rand.Intn(80)
	time.Sleep(time.Duration(ms) * time.Millisecond)
}

// RelayJitter добавляет микро-паузы во время передачи данных
func RelayJitter() {
	if rand.Intn(4) == 0 {
		time.Sleep(time.Duration(1+rand.Intn(4)) * time.Millisecond)
	}
}
