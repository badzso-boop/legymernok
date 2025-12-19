describe('Admin User List (Mocked Backend)', () => {
    it('should display the user list when logged in as admin', () => {
        // 1. API Mockolása (Intercept)
        // Amikor a frontend a /api/users-re lő, mi ezt adjuk vissza szerver helyett:
        cy.intercept('GET', '**/api/users', {
            statusCode: 200,
            body: [
                {
                    id: "mock-1",
                    username: "cypress_admin",
                    email: "cypress@test.com",
                    roles: ["ROLE_ADMIN"],
                    createdAt: "2024-01-01T10:00:00Z",
                    avatarUrl: null
                }
            ]
        }).as('getUsers');

        // 2. Bejelentkezés szimulálása
        // Mivel a ProtectedRoute a localStorage-ból olvassa a tokent,
        // beállítunk egy "kamu" tokent, ami tartalmazza a ROLE_ADMIN-t.
        // Ehhez egy egyszerű JWT struktúrát kell base64-elni, vagy csak bízunk benne,
        // hogy a frontend nem ellenőrzi az aláírást (a mi frontendünk decode-ol, de nem validál aláírást kliens oldalon).

        // Egy egyszerű payload: { sub: "cypress_admin", roles: ["ROLE_ADMIN"], exp: 9999999999 }
        // Ennek a Base64 kódolt változata (csak a payload része):
        const mockPayload = btoa(JSON.stringify({ sub: "cypress_admin", roles: ["ROLE_ADMIN"], exp:9999999999 }));
        const mockToken = `header.${mockPayload}.signature`;

        cy.visit('/admin/users', {
            onBeforeLoad(win) {
                win.localStorage.setItem('token', mockToken);
            }
        });

        // 3. Ellenőrzés
        // Megvárjuk, míg a kérés befut
        cy.wait('@getUsers');

        // Látnunk kell a mockolt adatot
        cy.contains('cypress_admin').should('be.visible');
        cy.contains('cypress@test.com').should('be.visible');

        // Ellenőrizzük, hogy az Admin chip megjelenik-e
        cy.contains('ADMIN').should('exist');
    });
});