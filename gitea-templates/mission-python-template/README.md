# Mission Template: Add Two Numbers (Python)

Ez a repó a Mission Forge-ban a küldetés **készítőjének** saját munkarepója —
a kadét sosem látja közvetlenül ezt a README-t; a feladatleírás a Forge
"Description" mezőjéből (`Mission.descriptionMarkdown`) jelenik meg neki.

## Fájlok

- **`solution.py`** — a referenciamegoldás. Ezt a készítő tölti ki a valódi,
  működő kóddal. **Ez sose kerül át a kadét saját repójába** — csak a CI
  ellenőrzéshez kell itt, a küldetés jóváhagyásakor.
- **`starter.py`** — a kadét kiindulási kódváza. Amikor egy kadét elindítja a
  küldetést, ennek a tartalma kerül át hozzá `solution.py` néven (hogy a
  teszt importja érvényben maradjon). Lehet teljesen üres, vagy tartalmazhat
  függvényszignatúrát/TODO-t — a készítő döntése.
- **`test_solution.py`** — a pytest unit tesztek. Ezek változatlanul
  átkerülnek a kadéthoz, és a lejátszóban **írásvédettek** — a kadét látja,
  mit várunk tőle, de nem módosíthatja.
- **`.gitea/workflows/ci.yml`** — a CI workflow, ami a `mission-verifier`
  action-t hívja `pytest -v` test_command-mal.

## Hogyan teszteld a saját megoldásodat készítőként

1. Töltsd ki a `solution.py`-t a working kóddal.
2. `pip install -r requirements.txt && pytest` — ellenőrizd, hogy a tesztek
   zöldek.
3. Töltsd ki a `starter.py`-t úgy, ahogy a kadétnak látnia kell (üresen vagy
   stub-bal).
