<template>
  <div class="login-page">
    <!-- ===== 背景:亮蓝渐变 + 光晕 ===== -->
    <div class="bg-grad"></div>
    <div class="glow glow-1"></div>
    <div class="glow glow-2"></div>
    <div class="glow glow-3"></div>
    <div class="grid-bg"></div>
    <div class="vignette"></div>

    <!-- ===== 透视网格地面(向远处延伸,缓慢流动) ===== -->
    <div class="floor-wrap"><div class="floor"></div></div>
    <i class="horizon"></i>

    <!-- ===== 地面线稿:远山 + 河流 + 输水管线/排污口 + 树木 + AI 识别目标 ===== -->
    <svg class="ground" viewBox="0 0 1920 900" preserveAspectRatio="xMidYMax slice" fill="none">
      <defs>
        <g id="pine" stroke="rgba(255,255,255,.55)" stroke-width="2" fill="rgba(255,255,255,.15)" stroke-linejoin="round">
          <path d="M0 -16 L-11 6 H11 Z" />
          <path d="M0 -4 L-14 22 H14 Z" />
          <path d="M0 22 V32" fill="none" />
        </g>
      </defs>

      <!-- 远山剪影 -->
      <path d="M-20 400 L120 348 L240 382 L380 328 L540 384 L660 342 L820 386 L960 334 L1120 382 L1260 340 L1420 384 L1560 342 L1720 386 L1940 350 V400 H-20 Z"
            fill="rgba(255,255,255,.11)" />
      <path d="M-20 400 L180 370 L320 392 L470 362 L630 394 L790 368 L950 394 L1110 364 L1280 392 L1440 366 L1610 392 L1780 368 L1940 388 V400 H-20 Z"
            fill="rgba(255,255,255,.07)" />

      <!-- 河流:岸带 + 水面 + 两组反向流动纹 -->
      <path d="M-40 560 C 240 505, 430 600, 700 570 C 950 542, 1130 655, 1390 615 C 1570 588, 1770 640, 1960 605"
            stroke="rgba(255,255,255,.14)" stroke-width="76" stroke-linecap="round" />
      <path d="M-40 560 C 240 505, 430 600, 700 570 C 950 542, 1130 655, 1390 615 C 1570 588, 1770 640, 1960 605"
            stroke="rgba(255,255,255,.22)" stroke-width="52" />
      <path class="river-flow" d="M-40 560 C 240 505, 430 600, 700 570 C 950 542, 1130 655, 1390 615 C 1570 588, 1770 640, 1960 605"
            stroke="rgba(255,255,255,.5)" stroke-width="2.4" stroke-dasharray="30 26" />
      <path class="river-flow2" d="M-40 572 C 240 517, 430 612, 700 582 C 950 554, 1130 667, 1390 627 C 1570 600, 1770 652, 1960 617"
            stroke="rgba(255,255,255,.32)" stroke-width="1.6" stroke-dasharray="16 34" />

      <!-- 地面等高线 -->
      <path d="M80 705 Q 520 645, 980 695" stroke="rgba(255,255,255,.13)" stroke-width="1.5" stroke-dasharray="5 11" />
      <path d="M320 795 Q 860 730, 1540 790" stroke="rgba(255,255,255,.1)" stroke-width="1.5" stroke-dasharray="5 11" />

      <!-- 岸边道路 -->
      <path d="M330 726 Q 470 708, 640 700" stroke="rgba(255,255,255,.16)" stroke-width="20" />
      <path d="M330 726 Q 470 708, 640 700" stroke="rgba(255,255,255,.3)" stroke-width="1.4" stroke-dasharray="18 16" />

      <!-- 输水管线 + 支墩 + 阀门 + 排污口 -->
      <g>
        <path d="M1150 508 H1960" stroke="rgba(255,255,255,.5)" stroke-width="4.5" />
        <g stroke="rgba(255,255,255,.38)" stroke-width="2.5">
          <path d="M1236 510 V530" /><path d="M1356 510 V530" /><path d="M1476 510 V530" />
          <path d="M1596 510 V530" /><path d="M1716 510 V530" /><path d="M1836 510 V530" />
        </g>
        <g stroke="rgba(255,255,255,.6)" stroke-width="2.2">
          <circle cx="1300" cy="508" r="9" />
          <path d="M1293 501 L1307 515 M1307 501 L1293 515" />
        </g>
        <rect x="1284" y="540" width="26" height="13" rx="2.5" stroke="rgba(255,255,255,.6)" stroke-width="2" fill="rgba(255,255,255,.18)" />
        <g stroke="rgba(255,255,255,.5)" stroke-width="1.8" stroke-linecap="round">
          <path class="of1" d="M1316 546 q 12 -5 24 0" />
          <path class="of2" d="M1316 551 q 12 -4 24 0" />
          <path class="of3" d="M1316 556 q 12 -3 24 0" />
        </g>
      </g>

      <!-- 岸线树木 -->
      <use href="#pine" transform="translate(300,520)" />
      <use href="#pine" transform="translate(345,534) scale(.8)" />
      <use href="#pine" transform="translate(568,568)" />
      <use href="#pine" transform="translate(872,632) scale(1.1)" />
      <use href="#pine" transform="translate(910,646) scale(.85)" />
      <use href="#pine" transform="translate(1185,690) scale(1.05)" />
      <use href="#pine" transform="translate(1224,702) scale(.8)" />
      <use href="#pine" transform="translate(1620,560) scale(.9)" />
      <use href="#pine" transform="translate(1662,574) scale(.7)" />

      <!-- ① 河边小船(green 识别框) -->
      <g class="tgt" style="animation-delay:-3.9s">
        <g stroke="#ffffff" stroke-width="2.4" fill="rgba(255,255,255,.16)" stroke-linejoin="round">
          <path d="M703 578 Q735 594 767 578 L758 566 H712 Z" />
          <rect x="726" y="556" width="18" height="10" rx="2" />
          <path d="M735 556 V544" fill="none" />
        </g>
        <rect x="675" y="536" width="120" height="62" stroke="rgba(74,222,128,.4)" stroke-width="1" stroke-dasharray="7 6" />
        <g stroke="#4ade80" stroke-width="2.5" fill="none">
          <path d="M675 552 V536 H691" /><path d="M779 536 H795 V552" />
          <path d="M675 582 V598 H691" /><path d="M779 598 H795 V582" />
        </g>
        <path d="M735 536 V522" stroke="rgba(74,222,128,.7)" stroke-width="1.5" />
        <rect x="683" y="496" width="104" height="26" rx="3" fill="rgba(4,30,66,.66)" stroke="rgba(74,222,128,.55)" stroke-width="1" />
        <text x="735" y="514" text-anchor="middle" font-size="15" font-weight="600" letter-spacing="2" fill="#eafff2"><tspan class="tc" style="animation-delay:-3.25s">船</tspan><tspan class="tc" style="animation-delay:-3.09s">只</tspan><tspan> </tspan><tspan class="tc" style="animation-delay:-2.93s">9</tspan><tspan class="tc" style="animation-delay:-2.77s">6</tspan><tspan class="tc" style="animation-delay:-2.61s">%</tspan></text>
        <circle cx="735" cy="570" r="2.5" fill="#4ade80">
          <animate attributeName="opacity" values="1;.2;1" dur="1.6s" repeatCount="indefinite" />
        </circle>
      </g>

      <!-- ② 岸边人员(cyan 识别框) -->
      <g class="tgt" style="animation-delay:-7.3s">
        <g stroke="#ffffff" stroke-width="2.6" fill="none" stroke-linecap="round">
          <circle cx="1010" cy="622" r="6.5" fill="rgba(255,255,255,.16)" />
          <path d="M1010 629 V656" />
          <path d="M1010 636 L997 649 M1010 636 L1023 646" />
          <path d="M1010 656 L1000 675 M1010 656 L1020 675" />
        </g>
        <rect x="966" y="606" width="88" height="80" stroke="rgba(34,211,238,.4)" stroke-width="1" stroke-dasharray="7 6" />
        <g stroke="#22d3ee" stroke-width="2.5" fill="none">
          <path d="M966 622 V606 H982" /><path d="M1038 606 H1054 V622" />
          <path d="M966 670 V686 H982" /><path d="M1038 686 H1054 V670" />
        </g>
        <path d="M1010 606 V592" stroke="rgba(34,211,238,.7)" stroke-width="1.5" />
        <rect x="962" y="562" width="96" height="26" rx="3" fill="rgba(4,30,66,.66)" stroke="rgba(34,211,238,.55)" stroke-width="1" />
        <text x="1010" y="580" text-anchor="middle" font-size="15" font-weight="600" letter-spacing="2" fill="#e6feff"><tspan class="tc" style="animation-delay:-6.65s">人</tspan><tspan class="tc" style="animation-delay:-6.49s">员</tspan><tspan> </tspan><tspan class="tc" style="animation-delay:-6.33s">9</tspan><tspan class="tc" style="animation-delay:-6.17s">2</tspan><tspan class="tc" style="animation-delay:-6.01s">%</tspan></text>
        <circle cx="1010" cy="646" r="2.5" fill="#22d3ee">
          <animate attributeName="opacity" values="1;.2;1" dur="1.9s" repeatCount="indefinite" />
        </circle>
      </g>

      <!-- ③ 路边车辆(green 识别框) -->
      <g class="tgt" style="animation-delay:-.5s">
        <g stroke="#ffffff" stroke-width="2.4" fill="rgba(255,255,255,.16)" stroke-linejoin="round">
          <rect x="436" y="676" width="52" height="26" rx="3" />
          <path d="M488 702 V684 Q488 678 494 678 H506 L516 690 V702 Z" />
          <path d="M492 684 H504 L511 692 H492 Z" fill="rgba(24,119,230,.35)" stroke-width="1.5" />
          <circle cx="452" cy="704" r="7" fill="none" />
          <circle cx="504" cy="704" r="7" fill="none" />
        </g>
        <rect x="395" y="660" width="150" height="64" stroke="rgba(74,222,128,.4)" stroke-width="1" stroke-dasharray="7 6" />
        <g stroke="#4ade80" stroke-width="2.5" fill="none">
          <path d="M395 676 V660 H411" /><path d="M529 660 H545 V676" />
          <path d="M395 708 V724 H411" /><path d="M529 724 H545 V708" />
        </g>
        <path d="M470 660 V646" stroke="rgba(74,222,128,.7)" stroke-width="1.5" />
        <rect x="418" y="620" width="104" height="26" rx="3" fill="rgba(4,30,66,.66)" stroke="rgba(74,222,128,.55)" stroke-width="1" />
        <text x="470" y="638" text-anchor="middle" font-size="15" font-weight="600" letter-spacing="2" fill="#eafff2"><tspan class="tc" style="animation-delay:.15s">车</tspan><tspan class="tc" style="animation-delay:.31s">辆</tspan><tspan> </tspan><tspan class="tc" style="animation-delay:.47s">9</tspan><tspan class="tc" style="animation-delay:.63s">4</tspan><tspan class="tc" style="animation-delay:.79s">%</tspan></text>
        <circle cx="470" cy="692" r="2.5" fill="#4ade80">
          <animate attributeName="opacity" values="1;.2;1" dur="1.4s" repeatCount="indefinite" />
        </circle>
      </g>
    </svg>

    <!-- ===== 雷达:同心环 + 匀速扫描扇 + 扫过亮点 ===== -->
    <svg class="radar" viewBox="0 0 220 220" fill="none">
      <circle cx="110" cy="110" r="96" stroke="rgba(255,255,255,.3)" stroke-width="3" stroke-dasharray="1.5 24" />
      <circle cx="110" cy="110" r="88" stroke="rgba(255,255,255,.32)" stroke-width="1.3" />
      <circle cx="110" cy="110" r="60" stroke="rgba(255,255,255,.24)" stroke-width="1.2" />
      <circle cx="110" cy="110" r="32" stroke="rgba(255,255,255,.18)" stroke-width="1.2" />
      <path d="M110 14 V206 M14 110 H206" stroke="rgba(255,255,255,.1)" stroke-width="1" />
      <g>
        <animateTransform attributeName="transform" type="rotate" from="0 110 110" to="360 110 110" dur="5.5s" repeatCount="indefinite" />
        <path d="M110 110 L110 22 A88 88 0 0 1 186.2 66 Z" fill="rgba(255,255,255,.09)" />
        <path d="M110 110 L110 22 A88 88 0 0 1 147.2 30.2 Z" fill="rgba(255,255,255,.16)" />
        <path d="M110 110 L110 22" stroke="rgba(255,255,255,.8)" stroke-width="1.8" />
      </g>
      <circle cx="165" cy="70" r="3" fill="#cfeaff">
        <animate attributeName="opacity" values="0;0;1;.25;0" keyTimes="0;.86;.9;.96;1" dur="5.5s" repeatCount="indefinite" />
      </circle>
      <circle cx="70" cy="152" r="3" fill="#4ade80">
        <animate attributeName="opacity" values="0;0;1;.25;0" keyTimes="0;.35;.4;.75;1" dur="5.5s" repeatCount="indefinite" />
      </circle>
      <circle cx="110" cy="110" r="3.2" fill="#ffffff" />
    </svg>

    <!-- ===== 无人机作业机组:整机缓慢横移 + 下视光锥 + 地面高亮 + 准星 ===== -->
    <div class="drone-rig">
      <div class="scan-cone"></div>
      <div class="scan-glow"></div>
      <div class="reticle"><i class="rr"></i><i class="rch"></i><i class="rcv"></i></div>

      <div class="drone">
        <div class="drone-bob">
          <svg viewBox="0 88 560 300" fill="none">
            <defs>
              <linearGradient id="dBody" x1="0" y1="0" x2=".5" y2="1">
                <stop offset="0" stop-color="#ffffff" /><stop offset="1" stop-color="#c5e0fb" />
              </linearGradient>
              <radialGradient id="dDisc" cx=".5" cy=".5" r=".5">
                <stop offset="0" stop-color="rgba(255,255,255,.5)" /><stop offset="1" stop-color="rgba(255,255,255,0)" />
              </radialGradient>
            </defs>

            <!-- 后旋翼(远,转速各异) -->
            <g>
              <rect x="158" y="150" width="24" height="13" rx="4" fill="url(#dBody)" stroke="#ffffff" stroke-width="1.5" />
              <circle cx="170" cy="150" r="42" stroke="rgba(255,255,255,.35)" stroke-width="1.5" />
              <circle cx="170" cy="150" r="37" fill="url(#dDisc)" />
              <g opacity=".92">
                <animateTransform attributeName="transform" type="rotate" from="0 170 150" to="360 170 150" dur=".46s" repeatCount="indefinite" />
                <path d="M132 152 Q170 141 208 152" stroke="#ffffff" stroke-width="4" stroke-linecap="round" />
                <path d="M172 112 Q160 150 172 188" stroke="#ffffff" stroke-width="4" stroke-linecap="round" opacity=".4" />
              </g>
              <circle cx="170" cy="150" r="6" fill="#eef6ff" stroke="#ffffff" stroke-width="1.5" />
            </g>
            <g>
              <rect x="378" y="150" width="24" height="13" rx="4" fill="url(#dBody)" stroke="#ffffff" stroke-width="1.5" />
              <circle cx="390" cy="150" r="42" stroke="rgba(255,255,255,.35)" stroke-width="1.5" />
              <circle cx="390" cy="150" r="37" fill="url(#dDisc)" />
              <g opacity=".92">
                <animateTransform attributeName="transform" type="rotate" from="0 390 150" to="360 390 150" dur=".54s" repeatCount="indefinite" />
                <path d="M352 152 Q390 141 428 152" stroke="#ffffff" stroke-width="4" stroke-linecap="round" />
                <path d="M392 112 Q380 150 392 188" stroke="#ffffff" stroke-width="4" stroke-linecap="round" opacity=".4" />
              </g>
              <circle cx="390" cy="150" r="6" fill="#eef6ff" stroke="#ffffff" stroke-width="1.5" />
            </g>

            <!-- 细机臂(双层描边) -->
            <g stroke-linecap="round">
              <path d="M244 216 C216 198,196 180,182 163" stroke="rgba(255,255,255,.5)" stroke-width="9" />
              <path d="M244 216 C216 198,196 180,182 163" stroke="#ffffff" stroke-width="3" />
              <path d="M316 216 C344 198,364 180,378 163" stroke="rgba(255,255,255,.5)" stroke-width="9" />
              <path d="M316 216 C344 198,364 180,378 163" stroke="#ffffff" stroke-width="3" />
              <path d="M216 262 C184 274,148 290,122 306" stroke="rgba(255,255,255,.5)" stroke-width="10" />
              <path d="M216 262 C184 274,148 290,122 306" stroke="#ffffff" stroke-width="3.4" />
              <path d="M344 262 C376 274,412 290,438 306" stroke="rgba(255,255,255,.5)" stroke-width="10" />
              <path d="M344 262 C376 274,412 290,438 306" stroke="#ffffff" stroke-width="3.4" />
            </g>

            <!-- 起落架 -->
            <g stroke-linecap="round" fill="none">
              <path d="M230 296 C218 318,208 336,202 352" stroke="rgba(255,255,255,.85)" stroke-width="6" />
              <path d="M188 354 H216" stroke="#ffffff" stroke-width="6" />
              <path d="M330 296 C342 318,352 336,358 352" stroke="rgba(255,255,255,.85)" stroke-width="6" />
              <path d="M344 354 H372" stroke="#ffffff" stroke-width="6" />
            </g>

            <!-- 机身:渐变 + 座舱面板 + 天线 -->
            <path d="M240 199 Q280 172 320 199 Z" fill="url(#dBody)" stroke="#ffffff" stroke-width="2" />
            <rect x="204" y="196" width="152" height="104" rx="26" fill="url(#dBody)" stroke="#ffffff" stroke-width="2.5" />
            <rect x="228" y="220" width="104" height="42" rx="10" fill="rgba(24,119,230,.07)" stroke="rgba(24,119,230,.22)" stroke-width="1.5" />
            <g stroke="rgba(24,119,230,.3)" stroke-width="2.2" stroke-linecap="round">
              <path d="M212 232 H224" /><path d="M212 241 H224" />
              <path d="M336 232 H348" /><path d="M336 241 H348" />
            </g>
            <circle cx="280" cy="241" r="13" fill="#0b3f8f" stroke="#7dd3fc" stroke-width="2" />
            <circle cx="280" cy="241" r="5.5" fill="#38bdf8" />
            <circle cx="276.5" cy="237.5" r="1.8" fill="#ffffff" />
            <path d="M280 174 V158" stroke="#ffffff" stroke-width="2.5" stroke-linecap="round" />
            <circle cx="280" cy="154" r="3" fill="#22d3ee">
              <animate attributeName="opacity" values="1;.25;1" dur="2.2s" repeatCount="indefinite" />
            </circle>

            <!-- 下视传感器 + 双轴云台相机 -->
            <circle cx="280" cy="292" r="10" fill="#0b3f8f" stroke="#7dd3fc" stroke-width="2" />
            <rect x="272" y="300" width="16" height="12" rx="3" fill="#dcebff" stroke="#ffffff" stroke-width="1.6" />
            <rect x="258" y="310" width="44" height="28" rx="9" fill="url(#dBody)" stroke="#ffffff" stroke-width="2" />
            <circle cx="280" cy="324" r="9.5" fill="#0b3f8f" stroke="#7dd3fc" stroke-width="2" />
            <circle cx="280" cy="324" r="4" fill="#38bdf8" />
            <circle cx="277" cy="321" r="1.6" fill="#ffffff" />

            <!-- 前旋翼(近) -->
            <g>
              <rect x="94" y="312" width="28" height="15" rx="5" fill="url(#dBody)" stroke="#ffffff" stroke-width="1.6" />
              <circle cx="108" cy="312" r="52" stroke="rgba(255,255,255,.38)" stroke-width="1.6" />
              <circle cx="108" cy="312" r="46" fill="url(#dDisc)" />
              <g opacity=".95">
                <animateTransform attributeName="transform" type="rotate" from="0 108 312" to="360 108 312" dur=".58s" repeatCount="indefinite" />
                <path d="M62 314 Q108 302 154 314" stroke="#ffffff" stroke-width="4.6" stroke-linecap="round" />
                <path d="M110 266 Q97 312 110 358" stroke="#ffffff" stroke-width="4.6" stroke-linecap="round" opacity=".4" />
              </g>
              <circle cx="108" cy="312" r="7.5" fill="#eef6ff" stroke="#ffffff" stroke-width="1.6" />
            </g>
            <g>
              <rect x="438" y="312" width="28" height="15" rx="5" fill="url(#dBody)" stroke="#ffffff" stroke-width="1.6" />
              <circle cx="452" cy="312" r="52" stroke="rgba(255,255,255,.38)" stroke-width="1.6" />
              <circle cx="452" cy="312" r="46" fill="url(#dDisc)" />
              <g opacity=".95">
                <animateTransform attributeName="transform" type="rotate" from="0 452 312" to="360 452 312" dur=".5s" repeatCount="indefinite" />
                <path d="M406 314 Q452 302 498 314" stroke="#ffffff" stroke-width="4.6" stroke-linecap="round" />
                <path d="M454 266 Q441 312 454 358" stroke="#ffffff" stroke-width="4.6" stroke-linecap="round" opacity=".4" />
              </g>
              <circle cx="452" cy="312" r="7.5" fill="#eef6ff" stroke="#ffffff" stroke-width="1.6" />
            </g>

            <!-- 状态灯 -->
            <circle cx="152" cy="286" r="4" fill="#4ade80">
              <animate attributeName="opacity" values="1;.2;1" dur="2.4s" repeatCount="indefinite" />
            </circle>
            <circle cx="408" cy="286" r="4" fill="#4ade80">
              <animate attributeName="opacity" values=".2;1;.2" dur="3.1s" repeatCount="indefinite" />
            </circle>
            <circle cx="334" cy="206" r="3.5" fill="#22d3ee">
              <animate attributeName="opacity" values="1;.3;1" dur="1.8s" repeatCount="indefinite" />
            </circle>
          </svg>
        </div>
      </div>
    </div>

    <!-- ===== 回传数据粒子:地面向无人机缓慢上升 ===== -->
    <div class="particles">
      <span class="pl" style="left:41%;--d:9s;--dl:-1.5s"><i></i></span>
      <span class="pl" style="left:44.5%;--d:8s;--dl:-4.2s"><i></i></span>
      <span class="pl" style="left:47.8%;--d:10.5s;--dl:-2.8s"><i></i></span>
      <span class="pl" style="left:51.4%;--d:7.5s;--dl:-6s"><i></i></span>
      <span class="pl" style="left:54.8%;--d:9.5s;--dl:-.5s"><i></i></span>
      <span class="pl" style="left:57.6%;--d:8.5s;--dl:-3.6s"><i></i></span>
      <span class="pl" style="left:43%;--d:11s;--dl:-7.4s"><i></i></span>
    </div>

    <!-- ===== 航空 HUD:四角角括号 + REC + 遥测文字 ===== -->
    <div class="hud">
      <i class="hc tl"></i><i class="hc tr"></i><i class="hc bl"></i><i class="hc br"></i>
      <span class="rec"><i></i>REC</span>
      <div class="telemetry">
        <span class="tm-hd">// UAV-01 TELEMETRY</span>
        <span class="tm-row">ALT 120M&nbsp;&nbsp;SPD 5.2M/S</span>
        <span class="tm-row">SAT 18&nbsp;&nbsp;&nbsp;&nbsp;LINK 5G&nbsp;&nbsp;<i class="tm-cur"></i></span>
      </div>
    </div>

    <!-- ===== 顶部栏 ===== -->
    <header class="top-bar">
      <div class="top-inner">
        <div class="top-left">
          <span class="top-emblem">
            <svg viewBox="0 0 120 120" fill="none">
              <circle cx="60" cy="60" r="54" fill="none" stroke="rgba(255,255,255,.6)" stroke-width="2" stroke-dasharray="7 5" />
              <path d="M60 22 L92 35 V64 C92 84 78 98 60 104 C42 98 28 84 28 64 V35 Z"
                    fill="#ffffff" stroke="#ffffff" stroke-width="2.4" />
              <circle cx="60" cy="58" r="4.2" fill="#1877e6" />
              <path d="M49 47 L56 54 M71 47 L64 54 M49 69 L56 62 M71 69 L64 62"
                    stroke="#1877e6" stroke-width="2.2" stroke-linecap="round" />
              <circle cx="48" cy="46" r="2.8" fill="#1877e6" /><circle cx="72" cy="46" r="2.8" fill="#1877e6" />
              <circle cx="48" cy="70" r="2.8" fill="#1877e6" /><circle cx="72" cy="70" r="2.8" fill="#1877e6" />
            </svg>
          </span>
          <span class="top-name">应急巡检平台</span>
          <i class="top-sep"></i>
          <span class="top-sub">应急管理 · 无人机巡检</span>
        </div>
        <div class="top-right">
          <span class="top-live"><i></i>系统运行正常</span>
          <i class="top-sep"></i>
          <span class="top-clock">{{ clock }}</span>
        </div>
      </div>
    </header>

    <!-- ===== 主体 ===== -->
    <div class="stage">
      <div class="brand">
        <div class="brand-inner">
          <h1 class="title">应急巡检平台</h1>
          <p class="title-en">EMERGENCY INSPECTION PLATFORM</p>
          <div class="rule"><i></i></div>
          <p class="sub">无人机空中巡查 · 隐患智能识别 · 应急事件联动<br />构建「感知—巡检—处置」一体化的应急管理体系</p>
          <div class="chips">
            <span class="chip"><i class="chip-dot"></i>无人机自动巡检</span>
            <span class="chip"><i class="chip-dot cyan"></i>AI 隐患识别</span>
            <span class="chip"><i class="chip-dot amber"></i>应急事件联动</span>
          </div>
        </div>
      </div>

      <div class="login-wrap">
        <div class="card-dock fade-in-up">
          <div class="login-card">
            <div class="card-head">
              <span class="card-badge">用户登录</span>
              <p class="card-sub">请使用应急管理实名账号登录</p>
            </div>
            <div class="card-body">
              <el-form :model="form" size="large" @keyup.enter="doLogin">
                <el-form-item>
                  <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" />
                </el-form-item>
                <el-form-item>
                  <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
                </el-form-item>
                <el-form-item>
                  <div class="captcha-row">
                    <el-input v-model="form.captcha" placeholder="请输入验证码" :prefix-icon="Key" maxlength="4"
                              style="flex: 1" @input="form.captcha = form.captcha.toUpperCase()" />
                    <div class="captcha-box" @click="loadCaptcha" title="点击刷新">
                      <div v-if="captchaLoading" class="captcha-loading"><el-icon class="is-loading"><Loading /></el-icon></div>
                      <img v-else-if="captchaSvg" :src="svgUrl" alt="验证码" />
                      <span v-else class="captcha-retry">点击获取</span>
                    </div>
                  </div>
                </el-form-item>
                <el-button class="submit-btn" :loading="loading" @click="doLogin">登 录</el-button>
              </el-form>
              <div class="engine-line">
                <span class="link-pulse"></span>巡检链路在线<span class="ver">UAV LINK ONLINE</span>
              </div>
              <div class="demo-line">演示账号:admin / admin123</div>
            </div>
          </div>
        </div>
        <p class="copy">© 2026 应急巡检平台 · 应急管理无人机巡检系统</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, Loading } from '@element-plus/icons-vue'
import http from '../api'

const router = useRouter()
const loading = ref(false)
const captchaSvg = ref('')
const captchaLoading = ref(false)
const form = reactive({ username: 'admin', password: '', captcha: '', cid: '' })

const svgUrl = computed(() =>
  captchaSvg.value ? 'data:image/svg+xml;utf8,' + encodeURIComponent(captchaSvg.value) : ''
)

/* 顶部时钟 */
const clock = ref('')
let clockTimer = null
function tick() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  clock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} 星期${'日一二三四五六'[d.getDay()]} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

onMounted(() => {
  loadCaptcha()
  tick()
  clockTimer = setInterval(tick, 1000)
})
onUnmounted(() => {
  clearInterval(clockTimer)
})

async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const data = await http.get('/auth/captcha')
    form.cid = data.cid
    captchaSvg.value = data.svg
    form.captcha = ''
  } finally {
    captchaLoading.value = false
  }
}

async function doLogin() {
  if (!form.username || !form.password) return ElMessage.warning('请输入账号和密码')
  if (!form.captcha) return ElMessage.warning('请输入验证码')
  loading.value = true
  try {
    const data = await http.post('/auth/login', form)
    localStorage.setItem('token', data.token)
    localStorage.setItem('nickname', data.nickname || data.username)
    localStorage.setItem('roleCode', data.roleCode || '')
    if (data.menus) localStorage.setItem('menus', JSON.stringify(data.menus))
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    loadCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ===== 亮蓝科技风:亮蓝底 + 无人机巡检作业大场景 ===== */
.login-page {
  --drone-w: clamp(250px, 23vw, 335px);
  position: relative; height: 100%; overflow: hidden;
  display: flex; flex-direction: column;
  background: #0b5ed7;
  color: #ffffff;
}

.bg-grad {
  position: absolute; inset: 0;
  background: linear-gradient(165deg, #0847b8 0%, #0f63d8 26%, #1877e6 50%, #2b9df5 74%, #6cc6fb 100%);
}
.glow { position: absolute; border-radius: 50%; filter: blur(150px); }
.glow-1 { width: 700px; height: 700px; background: #38bdf8; top: -280px; right: -60px; opacity: .5; }
.glow-2 { width: 560px; height: 560px; background: #7dd3fc; bottom: -260px; left: -180px; opacity: .38; }
.glow-3 { width: 520px; height: 520px; background: #00e5ff; top: 34%; left: 46%; opacity: .22; }

.grid-bg {
  position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, .07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, .07) 1px, transparent 1px);
  background-size: 58px 58px;
  mask-image: radial-gradient(ellipse at 50% 36%, #000 30%, transparent 80%);
}
.vignette {
  position: absolute; inset: 0; pointer-events: none;
  background: radial-gradient(ellipse 84% 74% at 50% 42%, transparent 46%, rgba(3, 26, 66, .42) 100%);
}

/* ===== 透视网格地面 ===== */
.floor-wrap {
  position: absolute; left: 0; right: 0; bottom: 0; height: 46%;
  perspective: 760px; overflow: hidden; pointer-events: none;
  mask-image: linear-gradient(180deg, transparent 0, #000 30%, rgba(0, 0, 0, .9) 82%, #000 100%);
}
.floor {
  position: absolute; left: -55%; right: -55%; top: -2px; height: 320%;
  background:
    repeating-linear-gradient(180deg, rgba(255, 255, 255, .13) 0 2px, transparent 2px 68px),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, .08) 0 2px, transparent 2px 112px);
  transform: rotateX(63deg); transform-origin: 50% 0;
  animation: floor-scroll 4.2s linear infinite;
}
@keyframes floor-scroll {
  from { background-position: 0 0, 0 0; }
  to { background-position: 0 68px, 0 0; }
}
.horizon {
  position: absolute; left: 0; right: 0; top: 54%; height: 2px; pointer-events: none;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, .38) 25%, rgba(255, 255, 255, .38) 75%, transparent);
}

/* ===== 地面线稿场景 ===== */
.ground { position: absolute; left: 0; right: 0; bottom: 0; width: 100%; height: 56%; pointer-events: none; }
.river-flow { animation: river-flow 7s linear infinite; }
@keyframes river-flow { to { stroke-dashoffset: -112; } }
.river-flow2 { animation: river-flow2 11s linear infinite reverse; }
@keyframes river-flow2 { to { stroke-dashoffset: -100; } }
.of1 { animation: discharge 2.8s ease-out infinite; }
.of2 { animation: discharge 3.4s ease-out infinite .6s; }
.of3 { animation: discharge 4s ease-out infinite 1.2s; }
@keyframes discharge {
  0% { transform: translateX(0); opacity: .8; }
  100% { transform: translateX(16px); opacity: 0; }
}

/* AI 目标识别框:出现 → 打字机标签 → 停留 → 淡出(目标间错开节奏) */
.tgt { opacity: 0; animation: tgt-cycle 10s ease-in-out infinite; }
@keyframes tgt-cycle {
  0% { opacity: 0; }
  6% { opacity: 1; }
  70% { opacity: 1; }
  82% { opacity: 0; }
  100% { opacity: 0; }
}
.tc { opacity: 0; animation: tc-in 10s linear infinite; }
@keyframes tc-in {
  0%, 4% { opacity: 0; }
  7%, 100% { opacity: 1; }
}

/* ===== 雷达 ===== */
.radar {
  position: absolute; left: 50%; top: 7%; width: clamp(110px, 11vw, 160px); aspect-ratio: 1;
  transform: translateX(-50%); opacity: .9; pointer-events: none; z-index: 2;
}

/* ===== 无人机机组(整机缓慢横移,光锥/高亮/准星随动) ===== */
.drone-rig {
  position: absolute; inset: 0; pointer-events: none; z-index: 3;
  animation: rig-sway 13s ease-in-out infinite alternate;
}
@keyframes rig-sway {
  from { transform: translateX(-14px); }
  to { transform: translateX(14px); }
}

/* 下视扫描光锥:扇形 + 横向扫描线来回脉冲 */
.scan-cone {
  position: absolute; left: 50%; top: 33%; height: 39.5%; width: min(30vw, 470px);
  transform: translateX(-50%);
  clip-path: polygon(44.6% 0, 55.4% 0, 100% 100%, 0 100%);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, .05), rgba(255, 255, 255, .3) 90%),
    repeating-linear-gradient(180deg, transparent 0 26px, rgba(255, 255, 255, .27) 26px 32px);
  animation: scan-move 2.6s ease-in-out infinite alternate;
  mask-image: linear-gradient(180deg, transparent 0, #000 20%, #000 82%, transparent 98%);
}
@keyframes scan-move {
  from { background-position: 0 0, 0 0; }
  to { background-position: 0 0, 0 58px; }
}

/* 地面被扫到位置的高亮 + 声呐扩散环 */
.scan-glow {
  position: absolute; left: 50%; top: 72.5%; width: min(32vw, 420px); height: 92px;
  transform: translate(-50%, -50%); border-radius: 50%; filter: blur(1px);
  background: radial-gradient(50% 50% at 50% 50%, rgba(255, 255, 255, .5), rgba(255, 255, 255, .16) 55%, transparent 75%);
  animation: glow-pulse 2.6s ease-in-out infinite;
}
@keyframes glow-pulse {
  0%, 100% { opacity: .55; }
  50% { opacity: .95; }
}
.scan-glow::after {
  content: ''; position: absolute; inset: 22% 14%;
  border: 1.5px solid rgba(255, 255, 255, .7); border-radius: 50%;
  animation: ring-x 2.6s ease-out infinite;
}
@keyframes ring-x {
  0% { transform: scale(.45); opacity: .85; }
  100% { transform: scale(1.45); opacity: 0; }
}

/* 跟踪准星 */
.reticle {
  position: absolute; left: 50%; top: 72.5%; width: 64px; height: 64px;
  transform: translate(-50%, -50%); opacity: .55;
}
.reticle .rr {
  position: absolute; inset: 8px;
  border: 1.5px dashed rgba(255, 255, 255, .75); border-radius: 50%;
  animation: ret-spin 16s linear infinite;
}
.reticle .rch {
  position: absolute; top: 50%; left: -12px; right: -12px; height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, .85), transparent);
}
.reticle .rcv {
  position: absolute; left: 50%; top: -12px; bottom: -12px; width: 1px;
  background: linear-gradient(180deg, transparent, rgba(255, 255, 255, .85), transparent);
}
@keyframes ret-spin { to { transform: rotate(360deg); } }

/* 主体大无人机:悬浮呼吸(上下 + 轻微俯仰) */
.drone {
  position: absolute; left: 50%; top: 18%;
  width: var(--drone-w); aspect-ratio: 560 / 300;
  transform: translateX(-50%);
  filter: drop-shadow(0 26px 42px rgba(3, 30, 76, .5));
}
.drone-bob { width: 100%; height: 100%; animation: drone-hover 4.6s ease-in-out infinite; }
@keyframes drone-hover {
  0%, 100% { transform: translateY(0) rotate(.5deg); }
  50% { transform: translateY(-14px) rotate(-.7deg); }
}
.drone-bob svg { width: 100%; height: 100%; display: block; }

/* ===== 回传数据粒子 ===== */
.particles { position: absolute; left: 0; right: 0; top: 35%; height: 36%; pointer-events: none; z-index: 2; }
.pl {
  position: absolute; top: 0; bottom: 0; width: 2px;
  animation: rise var(--d) linear infinite; animation-delay: var(--dl);
}
.pl i {
  position: absolute; bottom: -3px; left: -2px;
  width: 5px; height: 5px; border-radius: 50%;
  background: #fff; box-shadow: 0 0 10px 2px rgba(255, 255, 255, .6); opacity: 0;
  animation: dot-fade var(--d) linear infinite, dot-sway var(--d) ease-in-out infinite;
  animation-delay: var(--dl), var(--dl);
}
@keyframes rise {
  0% { transform: translateY(0); }
  100% { transform: translateY(-100%); }
}
@keyframes dot-fade {
  0% { opacity: 0; }
  12% { opacity: .95; }
  78% { opacity: .5; }
  100% { opacity: 0; }
}
@keyframes dot-sway {
  0%, 100% { transform: translateX(-7px); }
  50% { transform: translateX(7px); }
}

/* ===== 航空 HUD ===== */
.hud { position: absolute; top: 74px; left: 28px; right: 28px; bottom: 28px; pointer-events: none; z-index: 2; }
.hc { position: absolute; width: 26px; height: 26px; border: 2px solid rgba(255, 255, 255, .3); }
.hc.tl { top: 0; left: 0; border-right: 0; border-bottom: 0; }
.hc.tr { top: 0; right: 0; border-left: 0; border-bottom: 0; }
.hc.bl { bottom: 0; left: 0; border-right: 0; border-top: 0; }
.hc.br { bottom: 0; right: 0; border-left: 0; border-top: 0; }
.rec {
  position: absolute; top: 4px; right: 38px;
  display: flex; align-items: center; gap: 6px;
  font: 700 11px / 1 Consolas, 'Courier New', monospace;
  letter-spacing: 2px; color: rgba(255, 255, 255, .6);
}
.rec i {
  width: 7px; height: 7px; border-radius: 50%; background: #f87171;
  box-shadow: 0 0 8px rgba(248, 113, 113, .9);
  animation: live-blink 1.6s infinite;
}
.telemetry {
  position: absolute; left: 0; bottom: 5%;
  font: 11px / 2 Consolas, 'Courier New', monospace;
  letter-spacing: 1.5px; color: rgba(255, 255, 255, .58);
}
.tm-hd { display: block; color: rgba(255, 255, 255, .42); margin-bottom: 2px; }
.tm-row { display: block; }
.tm-cur {
  display: inline-block; width: 7px; height: 12px;
  background: rgba(255, 255, 255, .65); vertical-align: -1px;
  animation: live-blink 1.1s steps(1) infinite;
}

/* ===== 顶部栏 ===== */
.top-bar {
  position: relative; z-index: 5; flex-shrink: 0; height: 60px;
  background: rgba(6, 40, 96, .28);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(255, 255, 255, .16);
}
.top-inner {
  height: 100%; max-width: 1520px; margin: 0 auto; padding: 0 28px;
  display: flex; align-items: center; justify-content: space-between;
}
.top-left { display: flex; align-items: center; gap: 11px; }
.top-emblem { width: 32px; height: 32px; display: flex; flex-shrink: 0; }
.top-emblem svg { width: 100%; height: 100%; }
.top-name { font-size: 16px; font-weight: 700; letter-spacing: 2.5px; }
.top-sep { width: 1px; height: 16px; background: rgba(255, 255, 255, .35); margin: 0 3px; }
.top-sub { font-size: 12.5px; color: rgba(255, 255, 255, .8); letter-spacing: 1.5px; }
.top-right { display: flex; align-items: center; gap: 10px; }
.top-live { display: inline-flex; align-items: center; gap: 7px; font-size: 12.5px; color: rgba(255, 255, 255, .88); }
.top-live i {
  width: 6px; height: 6px; border-radius: 50%; background: #4ade80;
  box-shadow: 0 0 8px #4ade80; animation: live-blink 2s infinite;
}
@keyframes live-blink { 0%, 100% { opacity: 1; } 50% { opacity: .3; } }
.top-clock { font-size: 12.5px; color: rgba(255, 255, 255, .82); font-variant-numeric: tabular-nums; }

/* ===== 主体 ===== */
.stage { position: relative; z-index: 4; flex: 1; min-height: 0; display: flex; align-items: center; }

.brand { flex: 1; min-width: 0; display: flex; align-items: center; }
.brand-inner { padding: 0 0 0 7%; max-width: 640px; }

.title {
  font-size: 48px; font-weight: 800; letter-spacing: 10px; text-indent: 10px;
  color: #ffffff;
  text-shadow: 0 6px 26px rgba(3, 30, 76, .45);
  line-height: 1.18;
}
.title-en { margin-top: 10px; font-size: 11.5px; color: rgba(255, 255, 255, .72); letter-spacing: 3.6px; }

.rule { display: flex; align-items: center; width: 100%; max-width: 440px; margin: 20px 0 18px; }
.rule::before, .rule::after {
  content: ''; height: 1px; flex: 1;
  background: linear-gradient(90deg, rgba(255, 255, 255, .7), rgba(255, 255, 255, .06));
}
.rule::after { background: linear-gradient(90deg, rgba(255, 255, 255, .06), rgba(255, 255, 255, .7)); }
.rule i { width: 7px; height: 7px; margin: 0 11px; background: #ffffff; transform: rotate(45deg); }

.sub { font-size: 14px; color: rgba(255, 255, 255, .86); line-height: 2; letter-spacing: 1.2px; }

.chips { display: flex; flex-wrap: wrap; gap: 11px; margin-top: 28px; }
.chip {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 8px 15px; border-radius: 4px;
  background: rgba(255, 255, 255, .12);
  border: 1px solid rgba(255, 255, 255, .26);
  font-size: 12.5px; font-weight: 600; letter-spacing: 1.2px;
}
.chip-dot { width: 6px; height: 6px; border-radius: 50%; background: #ffffff; box-shadow: 0 0 8px #fff; }
.chip-dot.cyan { background: #7dd3fc; box-shadow: 0 0 8px #7dd3fc; }
.chip-dot.amber { background: #fdba74; box-shadow: 0 0 8px #fdba74; }

/* ===== 登录卡 ===== */
.login-wrap {
  width: 500px; flex-shrink: 0; margin-left: auto;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  position: relative; z-index: 4;
}
.card-dock { position: relative; }
.card-dock::before {
  content: ''; position: absolute; inset: -40px; border-radius: 40px;
  background: radial-gradient(closest-side, rgba(255, 255, 255, .26), transparent 76%);
  filter: blur(18px); z-index: -1;
}

.login-card {
  position: relative; width: 400px;
  border-radius: 14px; overflow: hidden;
  background: rgba(255, 255, 255, .96);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, .8);
  box-shadow: 0 30px 70px -24px rgba(3, 30, 76, .55), 0 4px 14px rgba(3, 30, 76, .12);
  color: #0f2444;
}
.login-card::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, #0847b8, #1877e6 34%, #38bdf8 66%, #7dd3fc);
  z-index: 1;
}

.card-head {
  position: relative; padding: 24px 32px 18px; text-align: center;
  background: linear-gradient(180deg, rgba(24, 119, 230, .08) 0%, transparent 100%);
  border-bottom: 1px solid rgba(24, 119, 230, .1);
}
.card-head::before {
  content: ''; position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(24, 119, 230, .05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(24, 119, 230, .05) 1px, transparent 1px);
  background-size: 22px 22px;
  mask-image: linear-gradient(180deg, #000 0%, transparent 74%);
  pointer-events: none;
}
.card-badge {
  position: relative; display: inline-block; padding: 6px 22px;
  font-size: 15px; font-weight: 700; color: #ffffff; letter-spacing: 4px; text-indent: 4px;
  border-radius: 3px;
  background: linear-gradient(90deg, #0f63d8, #1877e6 50%, #38bdf8);
  box-shadow: 0 6px 16px -6px rgba(24, 119, 230, .8);
}
.card-sub { position: relative; margin-top: 12px; font-size: 12px; color: #7b9cc4; letter-spacing: 1px; }

.card-body { padding: 22px 34px 24px; }
.el-form-item { margin-bottom: 18px; }

:deep(.el-input__wrapper) {
  border-radius: 6px; background: #f4f8ff;
  box-shadow: inset 0 0 0 1px #dbe7f8;
}
:deep(.el-input__wrapper:hover) { box-shadow: inset 0 0 0 1px #a9c8ee; }
:deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: inset 0 0 0 1.5px #1877e6, 0 0 0 4px rgba(24, 119, 230, .13) !important;
}
:deep(.el-input__inner) { color: #0f2444; }
:deep(.el-input__prefix-inner .el-icon),
:deep(.el-input__suffix-inner .el-icon) { color: #7b9cc4; }

.captcha-row { display: flex; gap: 10px; width: 100%; }
.captcha-box {
  width: 112px; height: 40px; flex-shrink: 0;
  border-radius: 6px; overflow: hidden; cursor: pointer;
  border: 1px solid #dbe7f8; background: #f4f8ff;
  display: flex; align-items: center; justify-content: center;
  transition: border-color .2s;
}
.captcha-box:hover { border-color: #1877e6; }
.captcha-box img { width: 100%; height: 100%; display: block; }
.captcha-loading { color: #7b9cc4; display: flex; }
.captcha-retry { font-size: 12px; color: #7b9cc4; }

.submit-btn {
  width: 100%; height: 46px; margin-top: 4px;
  font-size: 15px; font-weight: 700; letter-spacing: 10px; text-indent: 10px;
  border-radius: 6px;
  background: linear-gradient(90deg, #0847b8, #1877e6 30%, #38bdf8 60%, #7dd3fc 80%, #0847b8);
  background-size: 220% 100%; background-position: 0% 0;
  border: none; color: #ffffff;
  box-shadow: 0 10px 26px -8px rgba(24, 119, 230, .7);
  transition: background-position .6s ease, transform .25s, box-shadow .25s;
}
.submit-btn:hover {
  transform: translateY(-1px); background-position: 95% 0;
  box-shadow: 0 14px 32px -8px rgba(24, 119, 230, .85);
}

.engine-line {
  margin-top: 20px; padding-top: 14px;
  border-top: 1px dashed #dbe7f8;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  font-size: 12px; font-weight: 600; color: #46618a; letter-spacing: 1px;
}
.engine-line .ver { font-size: 10px; font-weight: 700; color: #1877e6; letter-spacing: 1.2px; }
.link-pulse { position: relative; width: 7px; height: 7px; border-radius: 50%; background: #1877e6; flex-shrink: 0; }
.link-pulse::after {
  content: ''; position: absolute; inset: 0; border-radius: 50%;
  border: 1.5px solid #1877e6; animation: link-ring 1.8s ease-out infinite;
}
@keyframes link-ring { 0% { transform: scale(1); opacity: .9; } 100% { transform: scale(2.6); opacity: 0; } }
.demo-line { margin-top: 9px; text-align: center; font-size: 12px; color: #9ab3d4; }

.copy {
  margin-top: 20px; text-align: center;
  font-size: 12px; color: rgba(255, 255, 255, .7); letter-spacing: 1px;
  text-shadow: 0 1px 8px rgba(3, 30, 76, .4);
}

/* ===== 响应式 ===== */
@media (max-width: 1500px) {
  .title { font-size: 42px; letter-spacing: 8px; text-indent: 8px; }
  .login-page { --drone-w: clamp(225px, 21vw, 295px); }
  .radar { width: clamp(104px, 10.5vw, 148px); }
}
@media (max-width: 1280px) {
  .brand-inner { max-width: 430px; }
  .login-wrap { width: 450px; }
  .login-card { width: 370px; }
  .title { font-size: 36px; letter-spacing: 6px; text-indent: 6px; }
  .login-page { --drone-w: clamp(195px, 19.5vw, 255px); }
  .drone { left: 52%; }
  .telemetry { font-size: 10px; }
}
@media (max-width: 1100px) {
  .top-sub { display: none; }
  .login-page { --drone-w: clamp(170px, 18vw, 225px); }
  .radar { width: 104px; top: 8.5%; }
  .telemetry { display: none; }
}
@media (max-width: 900px) {
  .brand { display: none; }
  .login-wrap { width: 100%; }
  .login-card { width: calc(100% - 48px); max-width: 400px; }
  .top-right { display: none; }
  .login-page { --drone-w: clamp(150px, 24vw, 200px); }
  .drone { top: 13%; left: 50%; }
  .radar { width: 92px; }
  .hud { left: 16px; right: 16px; }
}

/* 矮屏压缩 */
@media (max-height: 860px) {
  .title { font-size: 38px; }
  .rule { margin: 15px 0 14px; }
  .chips { margin-top: 20px; }
  .card-head { padding: 20px 32px 15px; }
  .card-body { padding: 18px 34px 20px; }
  .login-page { --drone-w: clamp(210px, 20vw, 270px); }
  .drone { top: 16%; }
  .radar { width: clamp(96px, 9vw, 128px); top: 8%; }
  .scan-cone { top: 30%; height: 41%; }
  .scan-glow, .reticle { top: 71%; }
}
@media (max-height: 720px) {
  .sub { display: none; }
  .rule { margin: 12px 0; }
  .radar { display: none; }
  .telemetry { display: none; }
  .login-page { --drone-w: clamp(180px, 18vw, 235px); }
  .drone { top: 14%; }
  .scan-cone { top: 27%; height: 43.5%; }
  .scan-glow, .reticle { top: 70.5%; }
  .particles { opacity: .55; }
}
</style>
