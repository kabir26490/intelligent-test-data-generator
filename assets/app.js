const links = document.querySelectorAll('.nav a');

links.forEach((link) => {
  link.addEventListener('click', (event) => {
    const href = link.getAttribute('href');
    const target = href ? document.querySelector(href) : null;

    if (target) {
      event.preventDefault();
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      history.replaceState(null, '', href);
    }

    links.forEach((item) => item.classList.remove('is-active'));
    link.classList.add('is-active');
  });
});
