/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#064E3B',
          dark: '#022c22',
          light: '#047857',
        },
        accent: {
          gold: '#FBBF24',
          purple: '#9D174D',
          magenta: '#A21CAF',
        },
        surface: {
          muted: '#F3F4F6',
          alert: '#FDF2F8',
        },
      },
      fontFamily: {
        sans: ['Inter', 'Roboto', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
