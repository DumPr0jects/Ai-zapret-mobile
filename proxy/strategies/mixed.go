package strategies

import (
	"time"
)

// Mixed комбинирует SplitSNI + FakeTLS + Padding
func Mixed(data []byte) ([]byte, time.Duration) {
	// 1. Применяем паддинг
	data = Padding(data)
	
	// 2. Применяем фейковую TLS-запись
	data = FakeTLS(data)
	
	// 3. Получаем задержку для split-эмуляции
	_, splitDelay := SplitSNI(data)
	
	return data, splitDelay
}
