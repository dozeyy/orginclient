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
      el.textContent = Math.round(value) + suffix;
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

  // ---------------------------------- Title screen: scroll-away parallax
  // The wordmark + menu drift up and fade as you scroll off the title
  // screen; the panorama grade deepens slightly (leaving the menu).
  const hero = document.getElementById("hero");
  const titleCenter = document.getElementById("titleCenter");
  const pano = document.getElementById("pano");
  if (hero && titleCenter && !reduceMotion) {
    let ticking = false;
    const update = () => {
      ticking = false;
      const p = clamp(window.scrollY / Math.max(1, hero.offsetHeight * 0.85), 0, 1);
      titleCenter.style.transform = `translateY(${(-p * 60).toFixed(1)}px)`;
      titleCenter.style.opacity = (1 - p * 1.25).toFixed(3);
      if (pano) pano.style.transform = `scale(${1 + p * 0.05})`;
    };
    window.addEventListener("scroll", () => {
      if (!ticking) {
        ticking = true;
        requestAnimationFrame(update);
      }
    }, { passive: true });
    update();
  }

  // -------------------------------------- Boot tour scroll scrubbing
  const scene = document.getElementById("client");
  if (scene) {
    const stepsCount = parseInt(scene.dataset.steps || "4", 10);
    const stepEls = scene.querySelectorAll(".scene__step");
    const fill = document.getElementById("sceneProgressFill");
    const world = document.getElementById("frameWorld");
    const gradeKnob = document.getElementById("gradeKnob");
    const gradeFill = document.getElementById("gradeFill");
    const gradeVal = scene.querySelector('[data-sim="sat"]');
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

      // final step scrubs the world's saturation; the panel's slider mirrors it
      if (world) {
        if (idx === stepsCount - 1) {
          const wave = Math.sin(intra * Math.PI * 2);
          const sat = 1.0 + wave * 0.9;
          world.style.setProperty("--world-sat", sat.toFixed(3));
          const pct = 50 + wave * 45;
          if (gradeKnob) gradeKnob.style.left = pct.toFixed(1) + "%";
          if (gradeFill) gradeFill.style.width = pct.toFixed(1) + "%";
          if (gradeVal) gradeVal.textContent = (sat / 1).toFixed(2) + "x";
        } else {
          world.style.setProperty("--world-sat", "1");
          if (gradeKnob) gradeKnob.style.left = "50%";
          if (gradeFill) gradeFill.style.width = "50%";
        }
      }
    };
    updateScene();
    window.addEventListener("scroll", updateScene, { passive: true });
    window.addEventListener("resize", updateScene);
  }

  // ------------------------------------------------- Live HUD "sims"
  // One clock drives every animated readout. Formats match HudElements
  // exactly: "FPS: n", "X: n", potion mm:ss, waypoint distances.
  const sims = (name) => document.querySelectorAll(`[data-sim="${name}"]`);
  const setAll = (name, text) => sims(name).forEach((el) => { el.textContent = text; });

  if (!reduceMotion && document.querySelector("[data-sim]")) {
    let fps = 1487, cx = 142, cz = -308, potion = 92;
    const fmtTime = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;

    setInterval(() => {
      fps = clamp(fps + Math.round((Math.random() - 0.5) * 60), 1320, 1640);
      setAll("fps", String(fps));
    }, 450);

    setInterval(() => {
      cx += Math.round(Math.random() * 3);
      cz -= Math.round(Math.random() * 2);
      setAll("cx", String(cx));
      setAll("cz", String(cz));
      potion = potion > 0 ? potion - 1 : 92;
      setAll("potion", fmtTime(potion));
      const t = performance.now() / 1000;
      setAll("wp-a", `${Math.round(143 + Math.sin(t * 0.6) * 18)}m`);
      setAll("wp-b", `${Math.round(402 + Math.cos(t * 0.4) * 24)}m`);
    }, 1000);

    // keystroke sim: W held most of the time, side keys tapped
    const keyEls = document.querySelectorAll("[data-key]");
    if (keyEls.length) {
      setInterval(() => {
        keyEls.forEach((el) => {
          const k = el.dataset.key;
          const on = k === "w" ? Math.random() > 0.15 : Math.random() > 0.72;
          el.classList.toggle("is-down", on);
        });
      }, 260);
    }
  }

  // ------------------------------------------- The mod menu recreation
  const menuPanel = document.getElementById("menuPanel");
  if (menuPanel) {
    const grid = document.getElementById("menuGrid");
    const settings = document.getElementById("menuSettings");
    const searchBox = document.getElementById("searchBox");
    const searchInput = document.getElementById("searchInput");
    const emptyMsg = document.getElementById("menuEmpty");
    const tabMods = document.getElementById("tabMods");
    const tabSettings = document.getElementById("tabSettings");
    const tooltip = document.getElementById("menuTooltip");
    const cards = Array.from(grid.querySelectorAll(".mcard"));

    // MODS / SETTINGS tabs
    const selectTab = (which) => {
      const mods = which === "mods";
      tabMods.classList.toggle("is-active", mods);
      tabSettings.classList.toggle("is-active", !mods);
      grid.hidden = !mods;
      settings.hidden = mods;
      searchBox.style.visibility = mods ? "visible" : "hidden";
    };
    tabMods.addEventListener("click", () => selectTab("mods"));
    tabSettings.addEventListener("click", () => selectTab("settings"));

    // GENERAL / PERFORMANCE sub-tabs
    const subGeneral = document.getElementById("subGeneral");
    const subPerformance = document.getElementById("subPerformance");
    settings.querySelectorAll("[data-sub]").forEach((btn) => {
      btn.addEventListener("click", () => {
        settings.querySelectorAll("[data-sub]").forEach((b) => b.classList.toggle("is-active", b === btn));
        subGeneral.hidden = btn.dataset.sub !== "general";
        subPerformance.hidden = btn.dataset.sub !== "performance";
      });
    });

    // search filter (same rule as layout(): case-insensitive name contains)
    searchInput.addEventListener("input", () => {
      const q = searchInput.value.trim().toLowerCase();
      let shown = 0;
      cards.forEach((card) => {
        const hit = q === "" || card.dataset.name.toLowerCase().includes(q);
        card.classList.toggle("is-hidden", !hit);
        if (hit) shown++;
      });
      emptyMsg.hidden = shown > 0;
    });

    // per-card behaviour: toggle, star, options
    cards.forEach((card) => {
      const tog = card.querySelector(".mcard__tog");
      tog.addEventListener("click", () => {
        const on = tog.dataset.on !== "true";
        tog.dataset.on = String(on);
        tog.textContent = on ? "ENABLED" : "DISABLED";
      });
      const star = card.querySelector(".mcard__star");
      star.addEventListener("click", () => card.classList.toggle("is-fav"));
      card.querySelector(".mcard__opt").addEventListener("click", () => {
        showToast("Options live in game — press Right Shift.");
      });
    });

    // the in-game tooltip: mod descriptions on card hover, tips on rows
    if (canHover && tooltip) {
      const show = (text, x, y) => {
        tooltip.textContent = text;
        tooltip.hidden = false;
        const pad = 14;
        const w = tooltip.offsetWidth, h = tooltip.offsetHeight;
        let tx = x + pad, ty = y + pad;
        if (tx + w + 8 > window.innerWidth) tx = window.innerWidth - w - 8;
        if (ty + h + 8 > window.innerHeight) ty = y - h - 8;
        tooltip.style.left = tx + "px";
        tooltip.style.top = ty + "px";
      };
      const bindTip = (el, textFn) => {
        el.addEventListener("pointermove", (e) => {
          // suppress over the interactive bits, like the in-game hit-test
          if (e.target.closest(".mcard__tog, .mcard__opt, .mcard__star, .sw, .sl, .frost--dd")) {
            tooltip.hidden = true;
            return;
          }
          show(textFn(), e.clientX, e.clientY);
        });
        el.addEventListener("pointerleave", () => { tooltip.hidden = true; });
      };
      cards.forEach((card) => bindTip(card, () => card.dataset.desc));
      settings.querySelectorAll(".srow[data-tip]").forEach((row) => bindTip(row, () => row.dataset.tip));
    }

    // switches
    menuPanel.querySelectorAll(".sw").forEach((sw) => {
      sw.addEventListener("click", () => {
        sw.dataset.on = String(sw.dataset.on !== "true");
      });
    });

    // dropdown rows: click cycles the modes (Origin -> Vanilla -> ...)
    menuPanel.querySelectorAll(".frost--dd").forEach((dd) => {
      const modes = dd.dataset.modes.split(",");
      let i = 0;
      dd.addEventListener("click", () => {
        i = (i + 1) % modes.length;
        dd.innerHTML = modes[i] + " &#9662;";
      });
    });

    // pill sliders: pointer-draggable, stepped, live value label
    menuPanel.querySelectorAll(".sl").forEach((sl) => {
      const pill = sl.querySelector(".pill");
      const fill = sl.querySelector(".pill__fill");
      const knob = sl.querySelector(".pill__knob");
      const label = sl.querySelector("b");
      const min = parseFloat(sl.dataset.min), max = parseFloat(sl.dataset.max);
      const step = parseFloat(sl.dataset.step), fmt = sl.dataset.fmt || "";
      let value = parseFloat(sl.dataset.value);

      const paint = () => {
        const p = ((value - min) / (max - min)) * 100;
        fill.style.width = p + "%";
        knob.style.left = p + "%";
        label.textContent = Math.round(value) + fmt;
      };
      paint();

      const setFromX = (clientX) => {
        const r = pill.getBoundingClientRect();
        const p = clamp((clientX - r.left) / r.width, 0, 1);
        value = Math.round((min + p * (max - min)) / step) * step;
        value = clamp(value, min, max);
        paint();
      };
      let dragging = false;
      sl.addEventListener("pointerdown", (e) => {
        dragging = true;
        sl.setPointerCapture(e.pointerId);
        setFromX(e.clientX);
      });
      sl.addEventListener("pointermove", (e) => { if (dragging) setFromX(e.clientX); });
      sl.addEventListener("pointerup", () => { dragging = false; });
      sl.addEventListener("pointercancel", () => { dragging = false; });
    });
  }

  // ------------------------------------------------ Interactive spotlight
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
  }

  // ------------------------------------------------------------- Toast
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
  document.querySelectorAll("[data-toast]").forEach((el) => {
    el.addEventListener("click", (e) => {
      e.preventDefault();
      showToast(el.dataset.toast);
    });
  });
})();
