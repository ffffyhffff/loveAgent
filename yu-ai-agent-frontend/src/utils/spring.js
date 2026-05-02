/**
 * 弹簧物理动画工具
 * 提供弹簧阻力感的交互效果
 */

/**
 * 弹簧缓动函数
 * @param {number} tension - 张力（默认 300，越大越快到位）
 * @param {number} friction - 摩擦力（默认 20，越大越不弹）
 */
export const springTransition = (tension = 300, friction = 20) => {
  return `all ${0.6}s cubic-bezier(${0.34}, ${1.56}, ${0.64}, 1)`;
};

/**
 * CSS cubic-bezier 弹性曲线
 * overshoot 百分比，0.1 = 10% 过冲
 */
export const springCurve = (overshoot = 0.15) => {
  const p = overshoot;
  return `cubic-bezier(0.34, ${1 + p}, 0.64, 1)`;
};

/**
 * 弹簧缩放动画（hover/active）
 */
export const springScale = (element, scale = 1.05) => {
  element.style.transition = 'transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)';
  element.style.transform = `scale(${scale})`;
};

export const springScaleReset = (element) => {
  element.style.transition = 'transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)';
  element.style.transform = 'scale(1)';
};

/**
 * 弹性出现动画
 */
export const springAppear = (element, delay = 0) => {
  element.style.opacity = '0';
  element.style.transform = 'scale(0.8) translateY(20px)';

  setTimeout(() => {
    element.style.transition = 'all 0.6s cubic-bezier(0.34, 1.56, 0.64, 1)';
    element.style.opacity = '1';
    element.style.transform = 'scale(1) translateY(0)';
  }, delay);
};

/**
 * 弹性按钮点击效果
 */
export const springButtonEffect = (element) => {
  element.addEventListener('mousedown', () => {
    element.style.transition = 'transform 0.1s ease-in';
    element.style.transform = 'scale(0.95)';
  });

  element.addEventListener('mouseup', () => {
    element.style.transition = 'transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)';
    element.style.transform = 'scale(1)';
  });

  element.addEventListener('mouseleave', () => {
    element.style.transition = 'transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)';
    element.style.transform = 'scale(1)';
  });
};

/**
 * 平滑滚动到底部
 */
export const smoothScrollToBottom = (element, duration = 400) => {
  if (!element) return;
  const start = element.scrollTop;
  const end = element.scrollHeight - element.clientHeight;
  const distance = end - start;

  if (distance <= 0) return;

  let startTime = null;
  const animate = (currentTime) => {
    if (!startTime) startTime = currentTime;
    const elapsed = currentTime - startTime;
    const progress = Math.min(elapsed / duration, 1);

    // 缓动：先快后慢
    const ease = 1 - Math.pow(1 - progress, 3);
    element.scrollTop = start + distance * ease;

    if (progress < 1) {
      requestAnimationFrame(animate);
    }
  };
  requestAnimationFrame(animate);
};

/**
 * 打字效果（逐字显示）
 */
export const typewriterEffect = (element, text, speed = 30) => {
  return new Promise((resolve) => {
    let index = 0;
    element.textContent = '';
    const interval = setInterval(() => {
      element.textContent += text[index];
      index++;
      if (index >= text.length) {
        clearInterval(interval);
        resolve();
      }
    }, speed);
  });
};
