package com.emergency.inspection.service;

import java.util.Locale;

/** 纯 Java 生成带干扰线/干扰点的验证码 SVG(免图片库依赖) */
final class CaptchaRenderer {

    private CaptchaRenderer() {
    }

    private static final int W = 120;
    private static final int H = 44;

    static String render(String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='").append(W).append("' height='").append(H)
                .append("' viewBox='0 0 ").append(W).append(' ').append(H).append("'>");
        sb.append("<rect width='100%' height='100%' fill='#eff4ff'/>");

        String[] lineColors = {"#bfd4f7", "#c9e2f5", "#dbe7fb"};
        for (int i = 0; i < 4; i++) {
            sb.append("<line x1='").append(rnd(0, W)).append("' y1='").append(rnd(0, H))
                    .append("' x2='").append(rnd(0, W)).append("' y2='").append(rnd(0, H))
                    .append("' stroke='").append(lineColors[i % lineColors.length]).append("' stroke-width='1'/>");
        }
        for (int i = 0; i < 14; i++) {
            sb.append("<circle cx='").append(rnd(0, W)).append("' cy='").append(rnd(0, H))
                    .append("' r='1' fill='#c3d5f2'/>");
        }

        String[] fills = {"#155eef", "#0e7ee0", "#2f6fe4", "#1d7ed8"};
        for (int i = 0; i < code.length(); i++) {
            double x = 14 + i * 26;
            double y = 30 + (Math.random() * 8 - 4);
            int rotate = (int) (Math.random() * 50 - 25);
            sb.append("<text x='").append(fmt(x)).append("' y='").append(fmt(y))
                    .append("' font-family='Arial, sans-serif' font-size='26' font-weight='700' fill='")
                    .append(fills[i % fills.length]).append("' transform='rotate(").append(rotate)
                    .append(' ').append(fmt(x)).append(' ').append(fmt(y)).append(")'>")
                    .append(code.charAt(i)).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    private static int rnd(int min, int max) {
        return min + (int) (Math.random() * (max - min));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
