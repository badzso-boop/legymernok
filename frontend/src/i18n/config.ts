import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

// Később ezeket külön JSON fájlokba szervezzük
const resources = {
    en: {
        translation: {
            welcome: "Welcome to LégyMérnök.hu",
            wip: "Work in Progress",
            login: "Login",
            username: "Username",
            password: "Password",
            email: "Email Address",
            usernameMandatory: "Username is mandatory",
            errorUsernameOrPwd: "Invalid username or password",
            passwordMandatory: "Password is mandatory",
            noProfile: "Don't have an account yet?",
            errorLogin: "An error occurred during login",
            register: "Register",
            dashboard: "Dashboard",
            admin: "Admin",
            cadet: "Cadet",
            landing: "The spaceship is under construction. We are coming soon!",
            missionInProgress: "Mission in progress...",
            usernameMin: "Username must be at least 3 characters",
            emailInvalid: "Invalid email address",
            passwordMin: "Password must be at least 6 characters",
            alreadyHaveAccount: "Already have an account?",
            errorRegister: "An error occurred during registration",
            errorUserExists: "Username or email is already taken"
        }
    },
    hu: {
        translation: {
            welcome: "Üdvözöl a LégyMérnök.hu",
            wip: "Fejlesztés alatt",
            login: "Bejelentkezés",
            username: "Felhasználónév",
            password: "Jelszó",
            email: "Email Cím",
            usernameMandatory: "Felhasználónév kötelező",
            errorUsernameOrPwd: "Hibás felhasználónév vagy jelszó",
            passwordMandatory: "Jelszó kötelező",
            noProfile: "Nincs még fiókod?",
            errorLogin: "Hiba történt a bejelentkezés során",
            register: "Regisztráció",
            dashboard: "Vezérlőpult",
            admin: "Adminisztrátor",
            cadet: "Kadét",
            landing: "Az űrhajó építés alatt áll. Hamarosan indulunk!", 
            missionInProgress: "A küldetés folyamatban.....",
            usernameMin: "Legalább 3 karakter",
            emailInvalid: "Érvényes email cím szükséges",
            passwordMin: "Legalább 6 karakter",
            alreadyHaveAccount: "Van már fiókod?",
            errorRegister: "Hiba történt a regisztráció során.",
            errorUserExists: "A felhasználónév vagy email már foglalt."
        }
    }
};

i18n
    .use(initReactI18next)
    .init({
        resources,
        lng: "hu", // Alapértelmezett nyelv
        fallbackLng: "en",
        interpolation: {
            escapeValue: false // React miatt nem kell
        }
    });

export default i18n;