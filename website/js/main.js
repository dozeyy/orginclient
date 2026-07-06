(() => {
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // Nav background on scroll
  const nav = document.getElementById("nav");
  const onScroll = () => nav.classList.toggle("scrolled", window.scrollY > 8);
  onScroll();
  window.addEventListener("scroll", onScroll, { passive: true });

  // Scroll reveal
  const revealEls = document.querySelectorAll(".reveal");
  const io = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry, i) => {
        if (entry.isIntersecting) {
          setTimeout(() => entry.target.classList.add("is-visible"), i * 40);
          io.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.15, rootMargin: "0px 0px -40px 0px" }
  );
  revealEls.forEach((el) => io.observe(el));

  // Count-up animation, reused for the big "3x" stat and the FPS bar values
  const animateCount = (el, { duration = 1200, from = 0 } = {}) => {
    const target = parseFloat(el.dataset.countTo);
    const suffix = el.dataset.suffix || "";
    if (reduceMotion) {
      el.textContent = target + suffix;
      return;
    }
    const start = performance.now();
    const step = (now) => {
      const p = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      const value = from + (target - from) * eased;
      const display = Number.isInteger(target) ? Math.round(value) : value.toFixed(1).replace(/\.0$/, "");
      el.textContent = display + suffix;
      if (p < 1) requestAnimationFrame(step);
      else el.textContent = target + suffix;
    };
    requestAnimationFrame(step);
  };

  const statEl = document.querySelector(".stat__number");
  if (statEl) {
    const statIo = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            animateCount(statEl, { duration: 1200, from: 1 });
            statIo.unobserve(statEl);
          }
        });
      },
      { threshold: 0.6 }
    );
    statIo.observe(statEl);
  }

  // FPS comparison bars: fill + count up together once scrolled into view
  const barsEl = document.querySelector(".bars");
  if (barsEl) {
    const barValues = barsEl.querySelectorAll(".bar-col__value");
    const barsIo = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            barsEl.classList.add("is-filled");
            barValues.forEach((el) => animateCount(el, { duration: 1100, from: 0 }));
            barsIo.unobserve(barsEl);
          }
        });
      },
      { threshold: 0.4 }
    );
    barsIo.observe(barsEl);
  }

  // Interactive spotlight: tight core tracks the pointer instantly, a softer
  // halo trails behind it (lerped each frame), both bloom on hover targets.
  const glowCore = document.getElementById("cursorGlowCore");
  const glowHalo = document.getElementById("cursorGlowHalo");
  if (glowCore && glowHalo && matchMedia("(hover: hover)").matches) {
    let targetX = -999, targetY = -999;
    let haloX = -999, haloY = -999;
    let started = false;

    window.addEventListener("pointermove", (e) => {
      targetX = e.clientX;
      targetY = e.clientY;
      glowCore.style.transform = `translate3d(${targetX}px, ${targetY}px, 0)`;
      if (!started) {
        started = true;
        haloX = targetX;
        haloY = targetY;
        tick();
      }
    });

    const tick = () => {
      if (reduceMotion) {
        glowHalo.style.transform = `translate3d(${targetX}px, ${targetY}px, 0)`;
        return;
      }
      haloX += (targetX - haloX) * 0.12;
      haloY += (targetY - haloY) * 0.12;
      glowHalo.style.transform = `translate3d(${haloX}px, ${haloY}px, 0)`;
      requestAnimationFrame(tick);
    };

    const hoverTargets = document.querySelectorAll(
      "a, button, .bar-col, .feature-list__item, .compare-table tbody tr"
    );
    hoverTargets.forEach((el) => {
      el.addEventListener("mouseenter", () => {
        glowCore.classList.add("is-active");
        glowHalo.classList.add("is-active");
      });
      el.addEventListener("mouseleave", () => {
        glowCore.classList.remove("is-active");
        glowHalo.classList.remove("is-active");
      });
    });
  }

  // No public build yet — give honest, visible feedback instead of a dead link
  const toast = document.getElementById("toast");
  let toastTimer;
  const showToast = (msg) => {
    toast.textContent = msg;
    toast.classList.add("is-shown");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove("is-shown"), 3200);
  };
  document.querySelectorAll("#downloadBtn, #downloadBtnNav").forEach((btn) => {
    btn.addEventListener("click", (e) => {
      e.preventDefault();
      showToast("Windows build isn't public yet — check back for v1.0.");
    });
  });
})();
