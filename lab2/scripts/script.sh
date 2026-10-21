#!/bin/bash

# Используем GNOME Terminal
TERMINAL="/usr/bin/gnome-terminal"

# Пути к файлам и параметрам
SERVER_CMD="./lab2 server 5267; echo 'Сервер остановлен. Нажмите Enter...'; read"
CLIENT1_CMD="./lab2 client /home/USER_NAME/IdeaProjects/NET/lab2/test.mp4 0.0.0.0 5267"
CLIENT2_CMD="./lab2 client /home/USER_NAME/IdeaProjects/NET/lab2/test.txt 0.0.0.0 5267"

# Сбрасываем старые правила на всякий случай
sudo tc qdisc del dev lo root 2>/dev/null

echo "[*] Запуск сервера в отдельном окне GNOME Terminal..."
$TERMINAL -- bash -c "$SERVER_CMD" &
SERVER_PID=$!

echo "[*] Ожидание 1 секунду для инициализации сервера..."
sleep 1

echo "[*] Ограничение скорости сети до 1 Мбит/с на локальном интерфейсе (lo)..."

# Устанавливаем ограничение (требуются права root)
sudo tc qdisc add dev lo root netem rate 1mbit

echo "[*] Запуск первого клиента (mp4)..."
$TERMINAL -- bash -c "$CLIENT1_CMD; echo 'Клиент 1 завершил работу. Нажмите Enter...'; read" &

# Добавлена задержка в 1 секунду перед запуском второго клиента
sleep 1

echo "[*] Запуск второго клиента (txt)..."
$TERMINAL -- bash -c "$CLIENT2_CMD; echo 'Клиент 2 завершил работу. Нажмите Enter...'; read" &

echo ""
echo "[*] Все процессы запущены. Ограничение скорости активно."
read -p "Нажмите [Enter] в этом терминале, чтобы сбросить ограничения сети и завершить работу..."

echo "[*] Сброс ограничений скорости (удаление qdisc)..."
sudo tc qdisc del dev lo root 2>/dev/null

echo "[*] Готово!"
