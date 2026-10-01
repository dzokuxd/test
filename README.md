# 🌌 SectorSystem

Zaawansowany, w pełni rozproszony system zamkniętych sektorów Minecraft (Paper 1.21.x + Velocity) z obsługą systemu gildii, oparty o architekturę mikroserwisową z użyciem **Redis**, **NATS** oraz **MySQL**.

## 🚀 Główne funkcjonalności

- **Zamknięte Sektory**: Świat gry podzielony na odizolowane instancje (`guild`, `spawn`, `afk`) bez możliwości swobodnego przechodzenia.
- **Pełna synchronizacja gracza**: Ekwiipunek, zbroja, offhand, enderchest, HP, głód, exp, gamemode, latanie oraz efekty mikstur przenoszone są bezstratnie między sektorami.
- **Zintegrowany System Gildii**: Zaawansowany moduł gildii z terytoriami, monumentami (jaja, kryształy), wojnami (TNT), systemem punktów, bonusami i relacyjną bazą danych.
- **Inteligentny Proxy (Velocity)**: Router transferów, kolejkowanie graczy (Limbo), system autoryzacji (Auth), orkiestracja restartów sektorów i health-checki.
- **Common Service ("Brain")**: Osobny serwis monitorujący heartbeaty z sektorów, sprawdzający ich dostępność i zarządzający stanem w Redis.
- **Launcher**: Narzędzie CLI do automatycznego startowania, zatrzymywania i restartowania całego stacku serwerów (Zarządzanie procesami i logami).
- **Globalny Chat i Tablist**: Synchronizacja wiadomości i listy graczy w czasie rzeczywistym za pomocą NATS.

## 📦 Struktura projektu

```text
SectorSystem/
├── common/          # Wspólna biblioteka (Redis, NATS, MySQL, API, Modele, Konfiguracja)
├── common-service/  # Serwis "Brain" (monitoring heartbeatów, health-check, NATS <-> Redis)
├── paper/           # Plugin Paper (logika sektorów, system Gildii, komendy, chat, tablist)
├── proxy/           # Plugin Velocity (routing transferów, autoryzacja, limbo, orkiestrator)
├── launcher/        # Aplikacja CLI do zarządzania cyklem życia serwera (PID, JVM args)
└── pom.xml          # Konfiguracja Maven (Java 21)

🛠️ Wymagania
Java 21+
Maven 3.9+
Redis Server
NATS Server
Baza danych MySQL (wymagana dla systemu Gildii)
Velocity 3.x (jako Proxy)
Paper 1.21.x (osobna instancja na każdy sektor)