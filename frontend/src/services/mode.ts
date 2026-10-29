// Today's no-OTP presentation is the default on localhost and Vercel alike.
export const presentationMode =
  import.meta.env.VITE_PRESENTATION_MODE !== "false";
