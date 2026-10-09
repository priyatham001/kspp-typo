import React, { useEffect, useRef } from 'react';

interface RotatingDotsSphereProps {
  size?: number; // visual size in px
  dotCount?: number;
  className?: string;
}

interface Point3D {
  x: number;
  y: number;
  z: number;
  baseSize: number;
  alpha: number;
}

export const RotatingDotsSphere: React.FC<RotatingDotsSphereProps> = ({
  size = 144,
  dotCount = 750,
  className = ''
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 2;
    canvas.width = size * dpr;
    canvas.height = size * dpr;
    ctx.scale(dpr, dpr);

    const radius = size * 0.44;
    const centerX = size / 2;
    const centerY = size / 2;
    const fov = radius * 3.5;

    // Generate 3D points distributed on and near a sphere (Fibonacci lattice + volume scatter)
    const points: Point3D[] = [];
    
    // 1. Surface points with slight radial jitter (matches the reference stardust sphere)
    const surfaceCount = Math.floor(dotCount * 0.8);
    for (let i = 0; i < surfaceCount; i++) {
      const phi = Math.acos(1 - 2 * (i + 0.5) / surfaceCount);
      const theta = Math.PI * (1 + Math.sqrt(5)) * i;
      // slight jitter in radius for organic star cluster look
      const r = radius * (0.86 + Math.random() * 0.24);
      const x = r * Math.sin(phi) * Math.cos(theta);
      const y = r * Math.sin(phi) * Math.sin(theta);
      const z = r * Math.cos(phi);
      // Varying dot sizes like the uploaded image
      const rand = Math.random();
      const baseSize = rand > 0.94 ? 2.4 : rand > 0.75 ? 1.6 : rand > 0.3 ? 1.0 : 0.6;
      const alpha = 0.5 + Math.random() * 0.5;
      points.push({ x, y, z, baseSize, alpha });
    }

    // 2. Interior scatter points to give realistic spherical depth
    const interiorCount = dotCount - surfaceCount;
    for (let i = 0; i < interiorCount; i++) {
      const u = Math.random();
      const v = Math.random();
      const theta = u * 2.0 * Math.PI;
      const phi = Math.acos(2.0 * v - 1.0);
      const r = Math.cbrt(Math.random()) * (radius * 0.88);
      const x = r * Math.sin(phi) * Math.cos(theta);
      const y = r * Math.sin(phi) * Math.sin(theta);
      const z = r * Math.cos(phi);
      const rand = Math.random();
      const baseSize = rand > 0.9 ? 1.8 : rand > 0.5 ? 1.1 : 0.6;
      const alpha = 0.4 + Math.random() * 0.5;
      points.push({ x, y, z, baseSize, alpha });
    }

    let rotY = 0;
    let rotX = 0.25; // slight tilt to see 3D poles revolving
    let animId: number;

    const render = () => {
      // Continuous smooth rotation
      rotY += 0.012;
      rotX += 0.002;

      ctx.clearRect(0, 0, size, size);

      // Black background
      ctx.fillStyle = '#000000';
      ctx.fillRect(0, 0, size, size);

      const cosY = Math.cos(rotY);
      const sinY = Math.sin(rotY);
      const cosX = Math.cos(rotX);
      const sinX = Math.sin(rotX);

      // Sort points from back to front for proper optical blending
      const projected = points.map((p) => {
        // Rotate around Y axis
        const x1 = p.x * cosY - p.z * sinY;
        const z1 = p.x * sinY + p.z * cosY;

        // Rotate around X axis
        const y2 = p.y * cosX - z1 * sinX;
        const z2 = p.y * sinX + z1 * cosX;

        // Perspective projection
        const scale = fov / (fov + z2);
        const projX = centerX + x1 * scale;
        const projY = centerY + y2 * scale;

        // Depth factor: 0 (far back) to 1 (closest front)
        const depth = (z2 + radius) / (2 * radius);

        return {
          projX,
          projY,
          z2,
          depth,
          scale,
          baseSize: p.baseSize,
          alpha: p.alpha
        };
      });

      // Render back-to-front
      projected.sort((a, b) => a.z2 - b.z2);

      for (let i = 0; i < projected.length; i++) {
        const p = projected[i];
        const dotRadius = Math.max(0.4, p.baseSize * p.scale);
        const opacity = Math.min(1, Math.max(0.12, 0.15 + p.depth * 0.85)) * p.alpha;

        // White stardust point
        ctx.beginPath();
        ctx.arc(p.projX, p.projY, dotRadius, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255, 255, 255, ${opacity})`;
        ctx.fill();

        // Shimmering aura for closest bright particles
        if (p.depth > 0.75 && p.baseSize > 1.4) {
          ctx.beginPath();
          ctx.arc(p.projX, p.projY, dotRadius * 2.2, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(180, 240, 255, ${(p.depth - 0.75) * 0.45})`;
          ctx.fill();
        }
      }

      animId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animId);
    };
  }, [size, dotCount]);

  return (
    <canvas
      ref={canvasRef}
      style={{ width: size, height: size }}
      className={`block select-none pointer-events-none ${className}`}
    />
  );
};
