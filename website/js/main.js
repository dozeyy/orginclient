(() => {
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const canHover = matchMedia("(hover: hover)").matches;
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

  // ---------------------------------------------------------------- Nav
  const nav = document.getElementById("nav");
  const onNavScroll = () => nav.classList.toggle("scrolled", window.scrollY > 8);
  onNavScroll();
  window.addEventListener("scroll", onNavScroll, { passive: true });

  // ------------------------------------------------------ Scroll reveal
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

  // ------------------------------------------------- Count-up numbers
  // Any element with data-count-to animates from `from` to the target when
  // it scrolls into view (the 3x stat, the mods/versions headline counts).
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

  document.querySelectorAll("[data-count-to]").forEach((el) => {
    const from = el.classList.contains("stat__number") ? 1 : 0;
    const cIo = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            animateCount(el, { duration: 1200, from });
            cIo.unobserve(el);
          }
        });
      },
      { threshold: 0.6 }
    );
    cIo.observe(el);
  });

  // ------------------------------------------- Hero: orbital rings canvas
  // The Origin mark rebuilt in 3D: three tilted rings orbiting one centre,
  // reacting to the pointer and blooming outward as you scroll "through"
  // them (the igloo-style descent). Pure 2D canvas, no libraries.
  const canvas = document.getElementById("ringsCanvas");
  const heroInner = document.getElementById("heroInner");
  const hero = document.getElementById("hero");
  if (canvas && hero) {
    const ctx = canvas.getContext("2d");
    let w = 0, h = 0, dpr = 1;
    let px = 0, py = 0;        // pointer, -1..1
    let tPx = 0, tPy = 0;
    let heroP = 0;             // 0 at top, 1 when hero fully scrolled past

    const resize = () => {
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = hero.clientWidth;
      h = hero.clientHeight;
      canvas.width = w * dpr;
      canvas.height = h * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    resize();
    window.addEventListener("resize", resize);

    window.addEventListener("pointermove", (e) => {
      tPx = (e.clientX / window.innerWidth) * 2 - 1;
      tPy = (e.clientY / window.innerHeight) * 2 - 1;
    }, { passive: true });

    // Ring model: a circle in 3D, squashed toward the mark's ellipse ratio,
    // oriented at 0/60/120° like the logo, then globally rotated.
    const RINGS = [
      { r: 0.95, o: 0, speed: 1.0 },
      { r: 0.79, o: (Math.PI * 2) / 3, speed: -0.8 },
      { r: 0.63, o: (Math.PI * 4) / 3, speed: 1.25 },
    ];
    const SEGS = 120;
    const SQUASH = 0.42; // ry/rx of the flat mark, roughly

    const drawFrame = (time) => {
      ctx.clearRect(0, 0, w, h);

      const cx = w / 2;
      const cy = h / 2;
      const base = Math.min(w, h) * 0.40;
      const bloom = 1 + heroP * 2.2;              // rings expand as you scroll
      const fade = clamp(1 - heroP * 1.35, 0, 1); // ...and fade out
      if (fade <= 0.01) return;

      const gx = px * 0.35, gy = py * 0.3;        // pointer tilt
      const slow = time * 0.00012;

      for (const ring of RINGS) {
        const R = base * ring.r * bloom;
        const spin = slow * ring.speed * 2 + ring.o;

        // orientation: squash (tilt about X), logo angle (about Z), then
        // global pointer/time wobble (about X and Y)
        const tiltX = Math.acos(SQUASH) + gy * 0.4;
        const cosT = Math.cos(tiltX), sinT = Math.sin(tiltX);
        const zRot = ring.o + Math.sin(slow * 0.7) * 0.15;
        const cosZ = Math.cos(zRot), sinZ = Math.sin(zRot);
        const yRot = gx * 0.5 + slow * ring.speed;
        const cosY = Math.cos(yRot), sinY = Math.sin(yRot);

        let prev = null;
        for (let s = 0; s <= SEGS; s++) {
          const a = (s / SEGS) * Math.PI * 2 + spin;
          // circle in XY
          let x = Math.cos(a) * R, y = Math.sin(a) * R, z = 0;
          // tilt about X
          let y2 = y * cosT - z * sinT, z2 = y * sinT + z * cosT;
          y = y2; z = z2;
          // logo angle about Z
          let x2 = x * cosZ - y * sinZ; y2 = x * sinZ + y * cosZ;
          x = x2; y = y2;
          // wobble about Y
          x2 = x * cosY + z * sinY; z2 = -x * sinY + z * cosY;
          x = x2; z = z2;

          const persp = 620 / (620 + z);
          const sx = cx + x * persp;
          const sy = cy + y * persp;
          const depth = clamp((z + R) / (2 * R), 0, 1); // 0 = near, 1 = far

          if (prev) {
            ctx.beginPath();
            ctx.moveTo(prev[0], prev[1]);
            ctx.lineTo(sx, sy);
            ctx.strokeStyle = `rgba(245,245,245,${(0.06 + (1 - depth) * 0.24) * fade})`;
            ctx.lineWidth = 1 + (1 - depth) * 1.2;
            ctx.stroke();
          }
          prev = [sx, sy];
        }

        // the comet: one bright point orbiting each ring
        const ca = time * 0.0006 * ring.speed + ring.o * 2;
        let x = Math.cos(ca) * R, y = Math.sin(ca) * R, z = 0;
        let y2 = y * cosT - z * sinT, z2 = y * sinT + z * cosT; y = y2; z = z2;
        let x2 = x * cosZ - y * sinZ; y2 = x * sinZ + y * cosZ; x = x2; y = y2;
        x2 = x * cosY + z * sinY; z2 = -x * sinY + z * cosY; x = x2; z = z2;
        const persp = 620 / (620 + z);
        const sx = cx + x * persp, sy = cy + y * persp;
        const g = ctx.createRadialGradient(sx, sy, 0, sx, sy, 26 * persp);
        g.addColorStop(0, `rgba(255,255,255,${0.75 * fade})`);
        g.addColorStop(1, "rgba(255,255,255,0)");
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.arc(sx, sy, 26 * persp, 0, Math.PI * 2);
        ctx.fill();
      }
    };

    if (reduceMotion) {
      drawFrame(0); // one static frame, no loop
    } else {
      const loop = (time) => {
        px += (tPx - px) * 0.05;
        py += (tPy - py) * 0.05;
        heroP = clamp(window.scrollY / Math.max(1, hero.offsetHeight), 0, 1);
        // parallax the hero copy away as the rings bloom
        if (heroInner) {
          heroInner.style.transform = `translateY(${heroP * 60}px) scale(${1 - heroP * 0.08})`;
          heroInner.style.opacity = clamp(1 - heroP * 1.5, 0, 1);
        }
        if (heroP < 1.01) drawFrame(time);
        requestAnimationFrame(loop);
      };
      requestAnimationFrame(loop);
    }
  }

  // -------------------------------------- Flagship scene scroll scrubbing
  const scene = document.getElementById("client");
  if (scene) {
    const stepsCount = parseInt(scene.dataset.steps || "4", 10);
    const stepEls = scene.querySelectorAll(".scene__step");
    const fill = document.getElementById("sceneProgressFill");
    const world = document.getElementById("frameWorld");
    const gradeKnob = document.getElementById("gradeKnob");
    let lastStep = -1;

    const updateScene = () => {
      const rect = scene.getBoundingClientRect();
      const total = scene.offsetHeight - window.innerHeight;
      const p = clamp(-rect.top / Math.max(1, total), 0, 1);
      const idx = Math.min(stepsCount - 1, Math.floor(p * stepsCount));
      const intra = p * stepsCount - idx;

      if (idx !== lastStep) {
        lastStep = idx;
        scene.dataset.activeStep = String(idx);
        stepEls.forEach((el) => el.classList.toggle("is-active", parseInt(el.dataset.step, 10) === idx));
      }
      if (fill) fill.style.width = (p * 100).toFixed(2) + "%";

      // step 3 scrubs the world's saturation with the scroll position, and
      // the on-screen slider knob mirrors it
      if (world) {
        if (idx === stepsCount - 1) {
          const wave = Math.sin(intra * Math.PI * 2);
          world.style.setProperty("--world-sat", (0.9 + wave * 0.8).toFixed(3));
          if (gradeKnob) gradeKnob.style.left = (50 + wave * 45).toFixed(1) + "%";
        } else {
          world.style.setProperty("--world-sat", "0.85");
          if (gradeKnob) gradeKnob.style.left = "50%";
        }
      }
    };
    updateScene();
    window.addEventListener("scroll", updateScene, { passive: true });
    window.addEventListener("resize", updateScene);
  }

  // ------------------------------------------------- Live preview "sims"
  // One clock drives every animated readout in the mod cards + scene HUD.
  // Elements opt in via data-sim; missing elements just never update.
  const sims = (name) => document.querySelectorAll(`[data-sim="${name}"]`);
  const setAll = (name, text) => sims(name).forEach((el) => { el.textContent = text; });

  if (!reduceMotion && document.querySelector("[data-sim]")) {
    let fps = 1487, cps = 12, cx = 142, cz = -308, potion = 92;
    const fmtTime = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;

    // fast tick: fps + cps jitter
    setInterval(() => {
      fps = clamp(fps + Math.round((Math.random() - 0.5) * 60), 1320, 1640);
      setAll("fps", String(fps));
      setAll("fps2", String(fps));
      cps = clamp(cps + Math.round((Math.random() - 0.5) * 3), 7, 16);
      setAll("cps", String(cps));
      setAll("ping", `${9 + Math.floor(Math.random() * 14)}ms`);
    }, 450);

    // slow tick: walking coords, potion countdown, waypoint distances
    setInterval(() => {
      cx += Math.round(Math.random() * 3);
      cz -= Math.round(Math.random() * 2);
      setAll("cx", String(cx)); setAll("cx2", String(cx));
      setAll("cz", String(cz)); setAll("cz2", String(cz));
      potion = potion > 0 ? potion - 1 : 92;
      setAll("potion", fmtTime(potion));
      setAll("potion2", fmtTime(potion));
      const t = performance.now() / 1000;
      setAll("wp-a", `${Math.round(128 + Math.sin(t * 0.6) * 18)}m`);
      setAll("wp-a2", `${Math.round(128 + Math.sin(t * 0.6) * 18)}m`);
      setAll("wp-b", `${Math.round(402 + Math.cos(t * 0.4) * 24)}m`);
      setAll("wp-b2", `${Math.round(402 + Math.cos(t * 0.4) * 24)}m`);
    }, 1000);

    // mirrors for CSS-animated previews (zoom FOV, saturation readout)
    setInterval(() => {
      const t = performance.now();
      const zoomPhase = Math.abs(Math.sin((t / 5000) * Math.PI)); // matches zoom-inout 5s alternate
      setAll("fov", String(Math.round(30 - zoomPhase * 20)));
      const satPhase = Math.abs(Math.sin((t / 6000) * Math.PI));  // matches sat-knob 6s alternate
      setAll("sat", (0.05 + satPhase * 1.55).toFixed(2) + "x");
    }, 200);

    // toggle-sprint + weather text flips
    let flip = false;
    setInterval(() => {
      flip = !flip;
      setAll("sprint", flip ? "[Sneaking (Toggled)]" : "[Sprinting (Toggled)]");
      setAll("weather", flip ? "Thunder" : "Rain");
    }, 2600);

    // keystroke sims: W held most of the time, side keys tapped, clicks fast
    const keyEls = document.querySelectorAll("[data-key]");
    if (keyEls.length) {
      setInterval(() => {
        keyEls.forEach((el) => {
          const k = el.dataset.key;
          let on;
          if (k === "w") on = Math.random() > 0.15;
          else if (k === "lmb" || k === "rmb") on = Math.random() > 0.55;
          else if (k === "space") on = Math.random() > 0.8;
          else on = Math.random() > 0.72;
          el.classList.toggle("is-down", on);
        });
      }, 260);
    }
  }

  // ------------------------------------------------------- Card tilt
  if (canHover && !reduceMotion) {
    document.querySelectorAll("[data-tilt]").forEach((card) => {
      let raf = null;
      card.addEventListener("pointermove", (e) => {
        if (raf) return;
        raf = requestAnimationFrame(() => {
          raf = null;
          const r = card.getBoundingClientRect();
          const rx = ((e.clientY - r.top) / r.height - 0.5) * -5;
          const ry = ((e.clientX - r.left) / r.width - 0.5) * 5;
          card.style.transform = `perspective(800px) rotateX(${rx.toFixed(2)}deg) rotateY(${ry.toFixed(2)}deg) translateY(-2px)`;
        });
      });
      card.addEventListener("pointerleave", () => {
        card.style.transform = "";
      });
    });

    // Magnetic buttons: drift toward the pointer while hovered
    document.querySelectorAll(".btn--magnetic").forEach((btn) => {
      btn.addEventListener("pointermove", (e) => {
        const r = btn.getBoundingClientRect();
        const dx = e.clientX - (r.left + r.width / 2);
        const dy = e.clientY - (r.top + r.height / 2);
        btn.style.transform = `translate(${(dx * 0.22).toFixed(1)}px, ${(dy * 0.3).toFixed(1)}px)`;
      });
      btn.addEventListener("pointerleave", () => {
        btn.style.transform = "";
      });
    });
  }

  // --------------------------------- Scroll-velocity lean on the mod grid
  const modsGrid = document.getElementById("modsGrid");
  if (modsGrid && !reduceMotion) {
    let lastY = window.scrollY, vel = 0;
    const lean = () => {
      const y = window.scrollY;
      vel += ((y - lastY) * 0.06 - vel) * 0.12;
      lastY = y;
      const v = clamp(vel, -4, 4);
      modsGrid.style.transform = Math.abs(v) > 0.05 ? `skewY(${v.toFixed(2)}deg)` : "";
      requestAnimationFrame(lean);
    };
    requestAnimationFrame(lean);
  }

  // ------------------------------------------------ Interactive spotlight
  // Tight core tracks the pointer instantly, a softer halo trails behind it
  // (lerped each frame), both bloom on hover targets.
  const glowCore = document.getElementById("cursorGlowCore");
  const glowHalo = document.getElementById("cursorGlowHalo");
  if (glowCore && glowHalo && canHover) {
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
      "a, button, .mod-card, .stack__chip, .versions li, .flagship, .compare-table tbody tr"
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
