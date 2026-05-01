package strategies

import (
	"crypto/rand"
	"math/rand"
)

// Padding добавляет случайный паддинг к небольшим пакетам
func Padding(data []byte) []byte {
	if len(data) >= 512 {
		return data
	}
	paddingSize := 50 + rand.Intn(200)
	padding := make([]byte, paddingSize)
	rand.Read(padding)
	return append(data, padding...)
}
