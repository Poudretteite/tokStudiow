# tokStudiow

![CI](https://github.com/Poudretteite/tokStudiow/actions/workflows/ci.yml/badge.svg)

Aplikacja webowa odwzorowująca uczelniany system zarządzania tokiem studiów, z naciskiem na bezpieczeństwo. Projekt realizowany w ramach pracy inżynierskiej.

> Projekt w trakcie realizacji.

## Funkcjonalności
- Logowanie z tokenami JWT, rotacją refresh tokenów i wykrywaniem ich kradzieży
- Role: student, prowadzący, dziekanat, administrator
- Struktura uczelni: wydziały, kierunki, semestry, sale, przedmioty z formami zajęć
- Przebiegi studiów, grupy zajęciowe i zapisy
- Oceny cząstkowe, oceny końcowe z zatwierdzaniem, terminy poprawkowe, indeks ze średnią
- Obecności na zajęciach
- Dziennik audytu wszystkich zmian

## Bezpieczeństwo
- Autoryzacja na poziomie obiektów: dostęp do cudzych danych zwraca 404 (ochrona przed IDOR)
- Token dostępu tylko w pamięci przeglądarki, refresh token w ciasteczku `HttpOnly`, `Secure`, `SameSite=Strict`
- Hasła hashowane algorytmem BCrypt
- Dziennik audytu tylko do dopisywania (blokada `UPDATE`, `DELETE`, `TRUNCATE` w bazie)
- Zatwierdzone oceny końcowe są niezmienne
- CI: testy integracyjne i skan sekretów przy każdym pull requeście

## Stack
- Frontend: React, TypeScript, Mantine, Vite
- Backend: Java 21, Spring Boot 4, Spring Security, Gradle
- Baza danych: PostgreSQL 17, migracje Flyway
- Testy: JUnit 5, Testcontainers
- Infrastruktura: Docker Compose, GitHub Actions

## Struktura
```
apps/
├── api/    backend (Spring Boot)
└── web/    frontend (React)
compose.yaml    baza danych do pracy lokalnej
```

## Uruchomienie lokalne
Wymagania: Docker Desktop, JDK 21, Node.js 22+.

1. Konfiguracja:
   ```bash
   cp .env.example .env
   ```
   Uzupełnij wartości w `.env` (hasło do bazy, `JWT_SECRET`, konto administratora deweloperskiego).

2. Baza danych:
   ```bash
   docker compose up -d
   ```

3. Backend (http://localhost:8080):
   ```bash
   cd apps/api
   ./gradlew bootRun
   ```
   Migracje bazy wykonują się automatycznie. W trybie deweloperskim powstaje konto administratora z danych w `.env`.

4. Frontend (http://localhost:5173):
   ```bash
   cd apps/web
   npm install
   npm run dev
   ```

## Testy
```bash
cd apps/api
./gradlew test
```
Testy uruchamiają tymczasową bazę PostgreSQL w kontenerze, więc Docker musi być włączony.