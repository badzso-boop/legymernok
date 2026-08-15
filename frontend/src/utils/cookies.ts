/**
 * Kis, függőség nélküli cookie-helper — a projekt eddig sehol nem használt
 * cookie-t (mindenhol `localStorage`, pl. a JWT tokenhez), ezért nincs
 * meglévő `js-cookie`-szerű csomag. Csak admin táblázat-preferenciák
 * (szűrő/oldalméret) tárolására szolgál, nem érzékeny adatra.
 */

export function getCookie(name: string): string | null {
  const match = document.cookie.match(
    new RegExp(`(?:^|; )${encodeURIComponent(name)}=([^;]*)`),
  );
  return match ? decodeURIComponent(match[1]) : null;
}

export function setCookie(name: string, value: string, days: number): void {
  const expires = new Date(Date.now() + days * 24 * 60 * 60 * 1000).toUTCString();
  document.cookie = `${encodeURIComponent(name)}=${encodeURIComponent(value)}; expires=${expires}; path=/; SameSite=Lax`;
}
