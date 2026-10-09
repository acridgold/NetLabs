# Передача файла по TCP

## Сборка и запуск
```bash
docker build -t lab2 .

# сервер (файлы попадут в ./uploads)
mkdir -p uploads
docker run --rm -p 5000:5000 -v "$PWD/uploads:/data/uploads" --name ft-server lab2 server 5000

# клиент (отправляем файл ./big.iso на сервер на хосте)
docker run --rm -v "$PWD:/files:ro" --network host lab2 client /files/big.iso 127.0.0.1 5000
```
Без Docker: `gradle installDist && build/install/lab2/bin/lab2 server 5000`.

## Протокол
Клиент -> сервер: `int ALOOO(0x4E534B503637)`, `int длина имени`, `имя UTF-8`, `long размер`, `содержимое`.
Сервер -> клиент: `byte` статус (1 — успех, 0 — ошибка), затем закрытие соединения.
Все числа big-endian.
