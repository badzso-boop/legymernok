import React, { useRef, useEffect } from "react";

interface Props {
  launchingRocketIndex: number | null; // Melyik rakéta indul (0-3), vagy null
  launchProgress: number; // 0-tól növekszik (animációhoz)
}

const SpaceStationCanvas: React.FC<Props> = ({
  launchingRocketIndex,
  launchProgress,
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  // Rakéták definíciója (szín, pozíció arányosan)
  const rockets = [
    { color: "#ef4444", label: "STAR-SYS" }, // Piros (World)
    { color: "#3b82f6", label: "BASE" }, // Kék (Base)
    { color: "#eab308", label: "LOBBY" }, // Sárga (Lobby)
    { color: "#22c55e", label: "ARENA" }, // Zöld (Arena)
  ];

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let animationFrameId: number;
    let particles: {
      x: number;
      y: number;
      vx: number;
      vy: number;
      life: number;
      color: string;
    }[] = [];

    const resize = () => {
      // A szülő elem méretét vesszük fel
      const parent = canvas.parentElement;
      if (parent) {
        canvas.width = parent.clientWidth;
        canvas.height = parent.clientHeight;
      }
    };
    window.addEventListener("resize", resize);
    resize();

    const drawRocket = (
      x: number,
      y: number,
      color: string,
      isLaunching: boolean,
    ) => {
      const w = 20; // Rakéta szélessége
      const h = 60; // Rakéta magassága

      // Ha kilövik, rezegjen
      const shakeX =
        isLaunching && launchProgress < 0.2 ? (Math.random() - 0.5) * 5 : 0;

      // Magasság pozíció (ha indul, megy felfelé)
      // launchProgress: 0 -> áll, 0.1 -> beindul, 1.0 -> kint van az űrből
      // Egy exponenciális gyorsulást szimulálunk
      const liftOffY =
        isLaunching && launchProgress > 0
          ? Math.pow(launchProgress * 20, 2.5)
          : 0;

      const currentY = y - liftOffY;

      ctx.save();
      ctx.translate(x + shakeX, currentY);

      // Test
      ctx.fillStyle = "#e2e8f0"; // Ezüst test
      ctx.fillRect(-w / 2, -h, w, h);

      // ABLAK
      ctx.beginPath();
      ctx.arc(0, -h * 0.7, w * 0.25, 0, Math.PI * 2);
      ctx.fillStyle = "#bae6fd";
      ctx.fill();
      ctx.strokeStyle = "#475569";
      ctx.stroke();

      // Orr kúp
      ctx.beginPath();
      ctx.moveTo(-w / 2, -h);
      ctx.lineTo(0, -h - 10);
      ctx.lineTo(w / 2, -h);
      ctx.fillStyle = color; // Színes orr
      ctx.fill();

      // Szárnyak
      ctx.beginPath();
      ctx.moveTo(-w / 2, 0);
      ctx.lineTo(-w / 2 - 10, 10);
      ctx.lineTo(-w / 2, -10);
      ctx.fillStyle = color;
      ctx.fill();

      ctx.beginPath();
      ctx.moveTo(w / 2, 0);
      ctx.lineTo(w / 2 + 10, 10);
      ctx.lineTo(w / 2, -10);
      ctx.fillStyle = color;
      ctx.fill();

      // Fúvóka lángja (csak ha indul)
      if (isLaunching && launchProgress > 0.05) {
        ctx.beginPath();
        ctx.moveTo(-w / 2 + 5, 0);
        ctx.lineTo(0, 40 + Math.random() * 20);
        ctx.lineTo(w / 2 - 5, 0);
        ctx.fillStyle = "#f59e0b"; // Narancs
        ctx.fill();

        ctx.beginPath();
        ctx.moveTo(-w / 2 + 10, 0);
        ctx.lineTo(0, 20 + Math.random() * 10);
        ctx.lineTo(w / 2 - 10, 0);
        ctx.fillStyle = "#fff"; // Fehér mag
        ctx.fill();

        // Részecskék generálása (Füst)
        for (let i = 0; i < 5; i++) {
          particles.push({
            x: x + (Math.random() - 0.5) * 20,
            y: currentY + 10,
            vx: (Math.random() - 0.5) * 2,
            vy: 2 + Math.random() * 3,
            life: 1.0,
            color: i % 2 === 0 ? "#555" : "#888",
          });
        }
      }

      ctx.restore();
    };

    const render = () => {
      const { width, height } = canvas;

      // 1. Háttér (Égbolt)
      // Átmenet: Sötétkék (űr) -> Világosabb (horizont)
      ctx.fillStyle = "#0ea5e9";
      ctx.fillRect(0, 0, width, height);

      // NAP
      ctx.beginPath();
      ctx.arc(width * 0.15, height * 0.2, 30, 0, Math.PI * 2);
      ctx.fillStyle = "#fde047";
      ctx.fill();
      ctx.closePath();

      // TALAJ (ZÖLD FŰ)
      ctx.fillStyle = "#22c55e";
      ctx.fillRect(0, height - 80, width, 80);

      // 3. Rakéták kirajzolása
      const spacing = width / (rockets.length + 1);

      rockets.forEach((rocket, index) => {
        const x = spacing * (index + 1);
        const y = height - 70; // A talajon áll
        const isLaunching = index === launchingRocketIndex;

        ctx.beginPath();
        ctx.ellipse(x, y + 5, 40, 12, 0, 0, Math.PI * 2);
        ctx.fillStyle = "#a4abb4"; // Sötétszürke beton szín
        ctx.fill();
        ctx.closePath();

        drawRocket(x, y, rocket.color, isLaunching);
      });

      animationFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener("resize", resize);
    };
  }, [launchingRocketIndex, launchProgress]);

  return (
    <canvas
      ref={canvasRef}
      style={{ width: "100%", height: "100%", display: "block" }}
    />
  );
};

export default SpaceStationCanvas;
