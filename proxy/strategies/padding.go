package strategies

import (
	crand "crypto/rand"  // 🔧 Алиас для crypto/rand
	"math/rand"          // math/rand остаётся как rand
)

// Padding добавляет случайный паддинг к небольшим пакетам
func Padding(data []byte) []byte {
	if len(data) >= 512 {
		return data
	}
	// 🔧 math/rand для размера паддинга
	paddingSize := 50 + rand.Intn(200)
	padding := make([]byte, paddingSize)
	// 🔧 crypto/rand для криптографически безопасных байтов
	crand.Read(padding)
	return append(data, padding...)
}
