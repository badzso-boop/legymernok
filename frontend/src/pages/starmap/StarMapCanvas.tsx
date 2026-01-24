import React, { useRef, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { StarSystemResponse } from "../../types/starSystem";

interface Props {
  systems: StarSystemResponse[];
}

const StarMapCanvas: React.FC<Props> = ({ systems }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const navigate = useNavigate();
  const [hoveredSystem, setHoveredSystem] = useState<StarSystemResponse | null>(
    null,
  );

  const rotationRef = useRef<number>(0);
  const animationFrameRef = useRef<number>(0);

  // Rendszerek pozíciójának tárolása (hogy ne ugráljanak minden rendereléskor)
  // De mivel a systems tömb jön be, feltételezzük, hogy az ID állandó.
  // Generálunk nekik fix koordinátákat az ID alapján (pszeudo-random),
  // hogy mindig ugyanott legyenek.
  const getSystemPosition = (id: string, width: number, height: number) => {
    // Egyszerű hash alapú pozicionálás, hogy determinisztikus legyen
    let hash = 0;
    for (let i = 0; i < id.length; i++) {
      hash = id.charCodeAt(i) + ((hash << 5) - hash);
    }
    // Normalizáljuk 0.1 és 0.9 közé, hogy ne legyen a szélen
    const x = (Math.abs(hash % 1000) / 1000) * 0.8 + 0.1;
    const y = (Math.abs((hash >> 16) % 1000) / 1000) * 0.8 + 0.1;
    return { x: x * width, y: y * height };
  };

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // Canvas átméretezése az ablakhoz
    const resize = () => {
      const parent = canvas.parentElement;
      if (parent) {
        canvas.width = parent.clientWidth;
        canvas.height = parent.clientHeight;
      }
    };
    window.addEventListener("resize", resize);
    resize();

    const render = () => {
      rotationRef.current += 0.005;
      const rotation = rotationRef.current;

      const { width, height } = canvas;
      const centerX = width / 2;
      const centerY = height / 2;

      // 1. Törlés (Fekete)
      ctx.fillStyle = "black";
      ctx.fillRect(0, 0, width, height);

      // 2. Rács (Grid)
      ctx.strokeStyle = "rgba(0, 255, 0, 0.2)";
      ctx.lineWidth = 1;
      const gridSize = 100;

      // Függőleges vonalak
      for (let x = centerX % gridSize; x < width; x += gridSize) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, height);
        ctx.stroke();
      }
      // Vízszintes vonalak
      for (let y = centerY % gridSize; y < height; y += gridSize) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(width, y);
        ctx.stroke();
      }

      // 3. Radar Körök (Koncentrikus)
      ctx.strokeStyle = "rgba(0, 255, 0, 0.3)";
      for (let r = 100; r < Math.max(width, height); r += 200) {
        ctx.beginPath();
        ctx.arc(centerX, centerY, r, 0, Math.PI * 2);
        ctx.stroke();
      }

      // 4. Forgó Radar Csík (Scanline)
      const scanLength = Math.max(width, height);
      const gradient = ctx.createConicGradient(rotation, centerX, centerY);
      gradient.addColorStop(0, "rgba(0, 255, 0, 0.5)"); // Fényes eleje
      gradient.addColorStop(0.1, "rgba(0, 255, 0, 0)"); // Átlátszó vége
      gradient.addColorStop(1, "rgba(0, 255, 0, 0)");

      ctx.fillStyle = gradient;
      ctx.beginPath();
      ctx.arc(centerX, centerY, scanLength, 0, Math.PI * 2);
      ctx.fill();

      // 5. Rendszerek kirajzolása
      systems.forEach((sys) => {
        const pos = getSystemPosition(sys.id, width, height);

        // Csillag pötty
        ctx.beginPath();
        ctx.arc(pos.x, pos.y, 6, 0, Math.PI * 2);
        ctx.fillStyle = "#0f0";
        ctx.shadowBlur = 10;
        ctx.shadowColor = "#0f0";
        ctx.fill();
        ctx.shadowBlur = 0;

        // Név (ha hover, vagy mindig?)
        // Legyen mindig látható, de halványan, hoverre fényesen
        const isHovered = hoveredSystem?.id === sys.id;

        ctx.font = '16px "VT323", monospace';
        ctx.fillStyle = isHovered ? "#fff" : "rgba(0, 255, 0, 0.7)";
        ctx.textAlign = "center";
        ctx.fillText(sys.name.toUpperCase(), pos.x, pos.y + 20);

        if (isHovered) {
          // Extra infó hoverkor
          ctx.fillStyle = "#0f0";
          ctx.font = '12px "VT323", monospace';
          ctx.fillText(
            `COORDS: [${Math.floor(pos.x)}, ${Math.floor(pos.y)}]`,
            pos.x,
            pos.y + 35,
          );

          // Célkereszt
          ctx.strokeStyle = "#fff";
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(pos.x - 15, pos.y);
          ctx.lineTo(pos.x + 15, pos.y);
          ctx.moveTo(pos.x, pos.y - 15);
          ctx.lineTo(pos.x, pos.y + 15);
          ctx.stroke();
        }
      });

      animationFrameRef.current = requestAnimationFrame(render);
    };
    render();

    return () => {
      if (animationFrameRef.current)
        cancelAnimationFrame(animationFrameRef.current);
      window.removeEventListener("resize", resize);
    };
  }, [systems, hoveredSystem]);

  // Egér eseménykezelők a Canvas-on
  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;

    // Megkeressük, hogy van-e rendszer a közelben
    let found: StarSystemResponse | null = null;

    systems.forEach((sys) => {
      const pos = getSystemPosition(sys.id, canvas.width, canvas.height);
      // Távolság számítása (Pitagorasz)
      const dist = Math.sqrt(
        Math.pow(mouseX - pos.x, 2) + Math.pow(mouseY - pos.y, 2),
      );
      if (dist < 20) {
        // 20px sugarú körben érzékeljük
        found = sys;
      }
    });
    setHoveredSystem(found);
    canvas.style.cursor = found ? "pointer" : "default";
  };

  const handleClick = () => {
    if (hoveredSystem) {
      // Navigáció a részletes oldalra
      navigate(`/star-systems/${hoveredSystem.id}`);
    }
  };

  return (
    <canvas
      ref={canvasRef}
      onMouseMove={handleMouseMove}
      onClick={handleClick}
      style={{ display: "block", width: "100%", height: "100%" }}
    />
  );
};

export default StarMapCanvas;
