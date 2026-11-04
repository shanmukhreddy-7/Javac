# Frontend entry point

main.tsx mounts React into the root DOM element in index.html. App.tsx configures routes. AuthContext manages the signed-in student. marketplace/ contains jobs, messaging, earnings, notifications and admin screens. services/api.ts calls Java APIs with HTTP-only cookies and CSRF tokens.

React and TypeScript run in the browser; this is not a Java main method.
