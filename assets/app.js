const links = document.querySelectorAll('.nav a');

links.forEach((link) => {
  link.addEventListener('click', () => {
    links.forEach((item) => item.classList.remove('is-active'));
    link.classList.add('is-active');
  });
});
