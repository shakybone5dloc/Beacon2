// Picks the theme before the first paint so dark mode doesn't flash white (ThemeService takes
// over once the app starts). A file of its own rather than inline in index.html, so the
// content security policy can forbid inline scripts.
(function () {
  var saved;
  try {
    saved = localStorage.getItem('beacon.theme');
  } catch (e) {}
  var dark = saved === 'dark' || (saved !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
})();
