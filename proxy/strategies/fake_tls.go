package strategies

import (
	"math/rand"
)

// FakeTLS добавляет поддельную TLS-запись перед реальным ClientHello
func FakeTLS(data []byte) []byte {
	if len(data) < 6 || data[0] != 0x16 {
		return data
	}
	// Фейковый header: Content Type 0x16, Version 0x0301, Length random
	fake := []byte{0x16, 0x03, 0x01}
	fakeLen := 10 + rand.Intn(40)
	fake = append(fake, byte(fakeLen>>8), byte(fakeLen))
	
	// Добавляем случайный payload
	fakePayload := make([]byte, fakeLen)
	rand.Read(fakePayload)
	fake = append(fake, fakePayload...)
	
	return append(fake, data...)
}
