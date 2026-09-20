/**
 * 登录页 Three.js 场景:科技亮蓝 · 无人机机场自动巡检循环。
 *
 * 剧情循环:待命(舱盖关) → 舱盖开启 → 垂直起飞 → 沿航点闭合巡航(带转弯侧倾
 * 与下视扫描光锥) → 返航降落 → 舱盖关闭 → 待命,周而复始。
 *
 * 画布透明(alpha)叠在 CSS 亮蓝渐变底上;WebGL 不可用时构造函数抛错,
 * 由调用方捕获并保留纯 CSS 视觉。onTelemetry 以 ~4Hz 回传阶段/高度/速度/电量。
 */
import * as THREE from 'three'

const CYAN = 0x38bdf8
const SKY = 0x7fd4ff
const EDGE = 0x4cc3ff

/** 巡检航点:首尾都在机场正上方,形成「起飞 → 环厂巡航 → 回到机场上方」的开放曲线 */
const WPTS = [
  [0, 7.2, 0],
  [7, 8.2, -1],
  [12.5, 7.4, 5],
  [9, 9.0, 12.5],
  [-1, 8.0, 15],
  [-11, 7.0, 11],
  [-14, 8.6, 1],
  [-9, 7.6, -9],
  [0, 8.2, -13],
  [8, 9.4, -8],
  [5, 7.8, -2],
  [0, 7.2, 0]
]

const PHASES = [
  { key: 'IDLE', dur: 1.6 },
  { key: 'OPENING', dur: 1.3 },
  { key: 'TAKEOFF', dur: 2.8 },
  { key: 'PATROL', dur: 26 },
  { key: 'DESCEND', dur: 2.8 },
  { key: 'CLOSING', dur: 1.3 }
]
const CYCLE = PHASES.reduce((s, p) => s + p.dur, 0)

const PARK_Y = 1.62       // 停机坪上的停放高度
const HOVER_Y = 7.2       // 巡航起点悬停高度
const easeInOut = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2)
const clamp = (v, a, b) => Math.min(b, Math.max(a, v))

/** 径向渐变光斑贴图:舱内辉光 / 信标光晕共用 */
function glowTexture() {
  const c = document.createElement('canvas')
  c.width = c.height = 128
  const ctx = c.getContext('2d')
  const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 64)
  g.addColorStop(0, 'rgba(120, 210, 255, 0.9)')
  g.addColorStop(0.4, 'rgba(56, 189, 248, 0.35)')
  g.addColorStop(1, 'rgba(56, 189, 248, 0)')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, 128, 128)
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  return tex
}

export function createLoginScene(container, { onTelemetry } = {}) {
  const width = container.clientWidth || window.innerWidth
  const height = container.clientHeight || window.innerHeight

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setClearColor(0x000000, 0)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.75))
  renderer.setSize(width, height)
  container.appendChild(renderer.domElement)

  const scene = new THREE.Scene()
  const camera = new THREE.PerspectiveCamera(44, width / height, 0.1, 400)

  scene.add(new THREE.HemisphereLight(0x9fd0ff, 0x0a1e42, 1.25))
  const moon = new THREE.DirectionalLight(0xbfd9ff, 2.2)
  moon.position.set(-14, 24, 12)
  scene.add(moon)
  const dockGlowLight = new THREE.PointLight(CYAN, 26, 16, 2)
  dockGlowLight.position.set(0, 3.6, 0)
  scene.add(dockGlowLight)

  /* ---------- 地面:暗蓝圆盘 + 网格 + 同心技术环 ---------- */
  const ground = new THREE.Mesh(
    new THREE.CircleGeometry(48, 64),
    new THREE.MeshBasicMaterial({ color: 0x04173a, transparent: true, opacity: 0.62 })
  )
  ground.rotation.x = -Math.PI / 2
  scene.add(ground)

  const grid = new THREE.GridHelper(96, 48, 0x9fd0ff, 0x2c62b8)
  grid.material.transparent = true
  grid.material.opacity = 0.22
  grid.position.y = 0.02
  scene.add(grid)

  for (const [r, op] of [[7, 0.3], [12.5, 0.2], [19, 0.14]]) {
    const ring = new THREE.Mesh(
      new THREE.RingGeometry(r, r + 0.1, 72),
      new THREE.MeshBasicMaterial({ color: 0x67b7ff, transparent: true, opacity: op, side: THREE.DoubleSide })
    )
    ring.rotation.x = -Math.PI / 2
    ring.position.y = 0.04
    scene.add(ring)
  }

  /* ---------- 机场(Dock) ---------- */
  const dock = new THREE.Group()
  scene.add(dock)

  const metal = new THREE.MeshStandardMaterial({ color: 0x112b52, metalness: 0.55, roughness: 0.45 })
  const base = new THREE.Mesh(new THREE.CylinderGeometry(3.3, 3.75, 1.15, 40), metal)
  base.position.y = 0.575
  dock.add(base)

  const rim = new THREE.Mesh(
    new THREE.TorusGeometry(3.32, 0.06, 12, 64),
    new THREE.MeshBasicMaterial({ color: EDGE })
  )
  rim.rotation.x = Math.PI / 2
  rim.position.y = 1.15
  dock.add(rim)

  // 内井壁(舱盖打开后可见)
  const shaft = new THREE.Mesh(
    new THREE.CylinderGeometry(2.05, 2.05, 0.95, 36, 1, true),
    new THREE.MeshStandardMaterial({ color: 0x0c2144, metalness: 0.4, roughness: 0.6, side: THREE.DoubleSide })
  )
  shaft.position.y = 1.62
  dock.add(shaft)

  // 停机坪 + H 标
  const pad = new THREE.Mesh(
    new THREE.CircleGeometry(1.85, 40),
    new THREE.MeshBasicMaterial({ color: 0x0e3a70 })
  )
  pad.rotation.x = -Math.PI / 2
  pad.position.y = 1.16
  dock.add(pad)
  const padRing = new THREE.Mesh(
    new THREE.RingGeometry(1.32, 1.46, 48),
    new THREE.MeshBasicMaterial({ color: SKY, transparent: true, opacity: 0.85, side: THREE.DoubleSide })
  )
  padRing.rotation.x = -Math.PI / 2
  padRing.position.y = 1.17
  dock.add(padRing)
  const hMat = new THREE.MeshBasicMaterial({ color: 0xeaf4ff, transparent: true, opacity: 0.92 })
  for (const [x, w, d] of [[-0.3, 0.14, 0.78], [0.3, 0.14, 0.78], [0, 0.62, 0.13]]) {
    const bar = new THREE.Mesh(new THREE.BoxGeometry(w, 0.02, d), hMat)
    bar.position.set(x, 1.17, 0)
    dock.add(bar)
  }

  // 双半圆舱盖:向两侧滑动开启
  const doorMat = new THREE.MeshStandardMaterial({ color: 0x1a4275, metalness: 0.5, roughness: 0.4, side: THREE.DoubleSide })
  const doorL = new THREE.Mesh(new THREE.CircleGeometry(2.06, 30, Math.PI / 2, Math.PI), doorMat)
  const doorR = new THREE.Mesh(new THREE.CircleGeometry(2.06, 30, -Math.PI / 2, Math.PI), doorMat)
  for (const d of [doorL, doorR]) {
    d.rotation.x = -Math.PI / 2
    d.position.y = 2.1
    dock.add(d)
  }

  // 天线 + 红色航空障碍灯
  const pole = new THREE.Mesh(
    new THREE.CylinderGeometry(0.045, 0.045, 2.3, 10),
    new THREE.MeshStandardMaterial({ color: 0x2c62b8, metalness: 0.6, roughness: 0.4 })
  )
  pole.position.set(2.55, 2.25, -2.55)
  dock.add(pole)
  const beaconMat = new THREE.MeshBasicMaterial({ color: 0xff5470, transparent: true })
  const beacon = new THREE.Mesh(new THREE.SphereGeometry(0.1, 12, 12), beaconMat)
  beacon.position.set(2.55, 3.45, -2.55)
  dock.add(beacon)
  const glowTex = glowTexture()
  const beaconGlow = new THREE.Sprite(new THREE.SpriteMaterial({
    map: glowTex, color: 0xff7a90, transparent: true, opacity: 0.8, depthWrite: false
  }))
  beaconGlow.scale.setScalar(1.1)
  beaconGlow.position.copy(beacon.position)
  dock.add(beaconGlow)

  // 底座追逐灯
  const chase = []
  for (let i = 0; i < 10; i++) {
    const a = (i / 10) * Math.PI * 2
    const m = new THREE.MeshBasicMaterial({ color: 0x67b7ff, transparent: true, opacity: 0.5 })
    const dot = new THREE.Mesh(new THREE.SphereGeometry(0.07, 10, 10), m)
    dot.position.set(Math.cos(a) * 3.02, 1.22, Math.sin(a) * 3.02)
    dock.add(dot)
    chase.push(m)
  }

  // 舱内辉光(开门时亮起)
  const padGlow = new THREE.Sprite(new THREE.SpriteMaterial({
    map: glowTex, color: 0x38bdf8, transparent: true, opacity: 0, depthWrite: false
  }))
  padGlow.scale.setScalar(6)
  padGlow.position.set(0, 1.9, 0)
  dock.add(padGlow)

  // 机场向外扩散的扫描脉冲环 ×2
  const pulses = []
  for (let k = 0; k < 2; k++) {
    const m = new THREE.MeshBasicMaterial({ color: 0x67b7ff, transparent: true, opacity: 0, side: THREE.DoubleSide })
    const p = new THREE.Mesh(new THREE.RingGeometry(0.92, 1.0, 56), m)
    p.rotation.x = -Math.PI / 2
    p.position.y = 0.05
    scene.add(p)
    pulses.push({ mesh: p, mat: m, offset: k * 2 })
  }

  /* ---------- 巡检航线:虚线 + 航点标记 ---------- */
  const curve = new THREE.CatmullRomCurve3(WPTS.map((p) => new THREE.Vector3(...p)), false, 'catmullrom', 0.55)
  const pathLine = new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(curve.getPoints(240)),
    new THREE.LineDashedMaterial({ color: 0x86d5ff, dashSize: 0.75, gapSize: 0.5, transparent: true, opacity: 0.4 })
  )
  pathLine.computeLineDistances()
  scene.add(pathLine)

  const wpTotal = WPTS.length - 2
  const wpMarks = []
  for (let i = 1; i < WPTS.length - 1; i++) {
    const m = new THREE.MeshBasicMaterial({ color: 0x9fe0ff, transparent: true, opacity: 0.5 })
    const oct = new THREE.Mesh(new THREE.OctahedronGeometry(0.26), m)
    oct.position.copy(curve.getPointAt(i / (WPTS.length - 1)))
    scene.add(oct)
    wpMarks.push(oct)
  }

  /* ---------- 厂区剪影(航线外围的深蓝自发光建筑 + 罐区) ---------- */
  const bMat = new THREE.MeshStandardMaterial({
    color: 0x16386f, emissive: 0x0d2a66, emissiveIntensity: 0.85, metalness: 0.3, roughness: 0.6
  })
  // 加性混合亮线框 + 立面发光横带 ×2:深色剪影而不发黑,贴合霓虹科技风
  const eMat = new THREE.LineBasicMaterial({
    color: 0x7fc4ff, transparent: true, opacity: 0.95, blending: THREE.AdditiveBlending
  })
  const bandMat = new THREE.MeshBasicMaterial({
    color: 0x8fd4ff, transparent: true, opacity: 0.9, blending: THREE.AdditiveBlending, depthWrite: false
  })
  const BUILDINGS = [
    [22, 3.2, -14, 6, 4.5], [-24, 2.4, 10, 7, 4], [18, 4.2, 20, 5, 6],
    [-19, 2.8, -18, 6, 3.5], [27, 3.6, 7, 4.5, 5], [-7, 2.2, 25, 7, 3]
  ]
  for (const [x, h, z, w, d] of BUILDINGS) {
    const geo = new THREE.BoxGeometry(w, h, d)
    const box = new THREE.Mesh(geo, bMat)
    box.position.set(x, h / 2, z)
    scene.add(box)
    const edges = new THREE.LineSegments(new THREE.EdgesGeometry(geo), eMat)
    edges.position.copy(box.position)
    scene.add(edges)
    for (const hy of [0.82, 0.45]) {
      const band = new THREE.Mesh(new THREE.BoxGeometry(w + 0.06, 0.16, d + 0.06), bandMat)
      band.position.set(x, h * hy, z)
      scene.add(band)
    }
  }
  for (const [x, z, r, h] of [[26, -6, 1.5, 5], [-27, -4, 1.9, 6.5]]) {
    const geo = new THREE.CylinderGeometry(r, r, h, 22)
    const tank = new THREE.Mesh(geo, bMat)
    tank.position.set(x, h / 2, z)
    scene.add(tank)
    const edges = new THREE.LineSegments(new THREE.EdgesGeometry(geo, 30), eMat)
    edges.position.copy(tank.position)
    scene.add(edges)
    for (const hy of [0.7, 0.35]) {
      const ring = new THREE.Mesh(new THREE.TorusGeometry(r + 0.04, 0.09, 8, 36), bandMat)
      ring.rotation.x = Math.PI / 2
      ring.position.set(x, h * hy, z)
      scene.add(ring)
    }
  }

  /* ---------- 无人机 ---------- */
  const drone = new THREE.Group()   // 位置 + 航向(lookAt)
  const bank = new THREE.Group()    // 侧倾 / 上浮等局部姿态
  drone.add(bank)
  scene.add(drone)
  drone.position.set(0, PARK_Y, 0)

  const bodyMat = new THREE.MeshStandardMaterial({ color: 0xf2f7ff, metalness: 0.35, roughness: 0.3 })
  const darkMat = new THREE.MeshStandardMaterial({ color: 0x1b2f4f, metalness: 0.5, roughness: 0.45 })
  const body = new THREE.Mesh(new THREE.BoxGeometry(1.35, 0.42, 1.7), bodyMat)
  bank.add(body)
  const bodyEdges = new THREE.LineSegments(
    new THREE.EdgesGeometry(body.geometry),
    new THREE.LineBasicMaterial({ color: 0x9fc6ff, transparent: true, opacity: 0.55 })
  )
  bank.add(bodyEdges)
  const canopy = new THREE.Mesh(new THREE.BoxGeometry(0.95, 0.16, 1.1), new THREE.MeshStandardMaterial({ color: 0x1877e6, metalness: 0.4, roughness: 0.35 }))
  canopy.position.set(0, 0.27, -0.1)
  bank.add(canopy)

  // 四臂 + 电机 + 旋翼(+z 为机头)
  const rotors = []
  const rotorDiscMats = []
  for (const [sx, sz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) {
    const arm = new THREE.Mesh(new THREE.CylinderGeometry(0.055, 0.07, 1.5, 10), darkMat)
    arm.rotation.z = Math.PI / 2
    arm.rotation.y = sx * sz > 0 ? -Math.PI / 4 : Math.PI / 4
    arm.position.set(sx * 0.55, 0.1, sz * 0.55)
    bank.add(arm)

    const motor = new THREE.Mesh(new THREE.CylinderGeometry(0.14, 0.17, 0.24, 14), darkMat)
    motor.position.set(sx * 1.08, 0.22, sz * 1.08)
    bank.add(motor)

    const spin = new THREE.Group()
    spin.position.set(sx * 1.08, 0.38, sz * 1.08)
    const bladeMat = new THREE.MeshBasicMaterial({ color: 0xdff1ff, transparent: true, opacity: 0.9 })
    for (const ry of [0, Math.PI / 2]) {
      const blade = new THREE.Mesh(new THREE.BoxGeometry(1.3, 0.02, 0.1), bladeMat)
      blade.rotation.y = ry
      spin.add(blade)
    }
    const discMat = new THREE.MeshBasicMaterial({ color: 0xbfe3ff, transparent: true, opacity: 0.06, side: THREE.DoubleSide, depthWrite: false })
    const disc = new THREE.Mesh(new THREE.CircleGeometry(0.7, 24), discMat)
    disc.rotation.x = -Math.PI / 2
    spin.add(disc)
    bank.add(spin)
    rotors.push(spin)
    rotorDiscMats.push(discMat)
  }

  // 云台相机
  const gimbal = new THREE.Mesh(new THREE.SphereGeometry(0.19, 14, 14), darkMat)
  gimbal.position.set(0, -0.26, 0.5)
  bank.add(gimbal)
  const lens = new THREE.Mesh(
    new THREE.CylinderGeometry(0.075, 0.075, 0.1, 12),
    new THREE.MeshBasicMaterial({ color: CYAN })
  )
  lens.rotation.x = Math.PI / 2
  lens.position.set(0, -0.28, 0.66)
  bank.add(lens)

  // 航行灯:左红右绿 + 尾部白色频闪
  const navL = new THREE.Mesh(new THREE.SphereGeometry(0.06, 8, 8), new THREE.MeshBasicMaterial({ color: 0xff4d6d, transparent: true }))
  navL.position.set(-1.08, 0.3, 1.08)
  const navR = navL.clone()
  navR.material = new THREE.MeshBasicMaterial({ color: 0x2effa0, transparent: true })
  navR.position.set(1.08, 0.3, 1.08)
  const strobeMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true })
  const strobe = new THREE.Mesh(new THREE.SphereGeometry(0.06, 8, 8), strobeMat)
  strobe.position.set(0, 0.1, -0.95)
  bank.add(navL, navR, strobe)

  // 下视扫描光锥 + 地面瞄准环
  const coneMat = new THREE.MeshBasicMaterial({
    color: CYAN, transparent: true, opacity: 0.1, side: THREE.DoubleSide, depthWrite: false, blending: THREE.AdditiveBlending
  })
  const scanCone = new THREE.Mesh(new THREE.ConeGeometry(2.5, 6.4, 26, 1, true), coneMat)
  scanCone.position.y = -3.3
  scanCone.visible = false
  bank.add(scanCone)

  const reticle = new THREE.Group()
  reticle.visible = false
  for (const [r0, r1, op] of [[1.55, 1.78, 0.5], [0.55, 0.72, 0.65]]) {
    const r = new THREE.Mesh(
      new THREE.RingGeometry(r0, r1, 40),
      new THREE.MeshBasicMaterial({ color: SKY, transparent: true, opacity: op, side: THREE.DoubleSide, blending: THREE.AdditiveBlending, depthWrite: false })
    )
    r.rotation.x = -Math.PI / 2
    reticle.add(r)
  }
  reticle.position.y = 0.06
  scene.add(reticle)

  // 假投影:贴地暗斑随高度变淡放大
  const shadowMat = new THREE.MeshBasicMaterial({ color: 0x02102b, transparent: true, opacity: 0.35, depthWrite: false })
  const shadow = new THREE.Mesh(new THREE.CircleGeometry(1.05, 26), shadowMat)
  shadow.rotation.x = -Math.PI / 2
  shadow.position.y = 0.03
  scene.add(shadow)

  /* ---------- 粒子:高空星尘 + 机场上浮数据光点 ---------- */
  const starGeo = new THREE.BufferGeometry()
  const starPos = new Float32Array(300 * 3)
  for (let i = 0; i < 300; i++) {
    starPos[i * 3] = (Math.random() - 0.5) * 110
    starPos[i * 3 + 1] = 2 + Math.random() * 30
    starPos[i * 3 + 2] = (Math.random() - 0.5) * 110
  }
  starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3))
  const stars = new THREE.Points(starGeo, new THREE.PointsMaterial({
    color: 0xcfe6ff, size: 0.22, transparent: true, opacity: 0.75, blending: THREE.AdditiveBlending, depthWrite: false
  }))
  scene.add(stars)

  const moteCount = 26
  const moteGeo = new THREE.BufferGeometry()
  const motePos = new Float32Array(moteCount * 3)
  const moteSpeed = new Float32Array(moteCount)
  for (let i = 0; i < moteCount; i++) {
    const a = Math.random() * Math.PI * 2
    const r = 1 + Math.random() * 3.2
    motePos[i * 3] = Math.cos(a) * r
    motePos[i * 3 + 1] = Math.random() * 10
    motePos[i * 3 + 2] = Math.sin(a) * r
    moteSpeed[i] = 0.5 + Math.random() * 0.9
  }
  moteGeo.setAttribute('position', new THREE.BufferAttribute(motePos, 3))
  const motes = new THREE.Points(moteGeo, new THREE.PointsMaterial({
    color: SKY, size: 0.15, transparent: true, opacity: 0.8, blending: THREE.AdditiveBlending, depthWrite: false
  }))
  scene.add(motes)

  /* ---------- 相机:缓摆 + 鼠标视差 ---------- */
  const lookTarget = new THREE.Vector3(3.4, 4.8, 0)
  const camPos = new THREE.Vector3()
  const mouse = { x: 0, y: 0, tx: 0, ty: 0 }
  const onMouse = (e) => {
    const r = container.getBoundingClientRect()
    mouse.tx = ((e.clientX - r.left) / r.width - 0.5) * 2
    mouse.ty = ((e.clientY - r.top) / r.height - 0.5) * 2
  }
  // 挂 window:登录卡片悬浮在容器之上,容器自身收不到卡片区鼠标事件
  window.addEventListener('mousemove', onMouse)

  const onResize = () => {
    const w = container.clientWidth || window.innerWidth
    const h = container.clientHeight || window.innerHeight
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    renderer.setSize(w, h)
  }
  window.addEventListener('resize', onResize)

  /* ---------- 主循环 ---------- */
  const clock = new THREE.Clock()
  let rafId = 0
  let phase = 'IDLE'
  let openT = 0            // 舱盖开度 0..1
  let rotorSpeed = 0
  let bankAngle = 0
  let spd = 0
  const prevPos = new THREE.Vector3()
  let teleAcc = 0
  let battery = 98

  const vTmp = new THREE.Vector3()
  const vAhead = new THREE.Vector3()
  const vT0 = new THREE.Vector3()
  const vT1 = new THREE.Vector3()

  function tick() {
    rafId = requestAnimationFrame(tick)
    const dt = Math.min(clock.getDelta(), 0.05)
    const t = clock.elapsedTime

    // 阶段推进
    let acc = (t % CYCLE)
    let ph = PHASES[PHASES.length - 1]
    for (const p of PHASES) {
      if (acc < p.dur) { ph = p; break }
      acc -= p.dur
    }
    phase = ph.key
    const u = clamp(acc / ph.dur, 0, 1)

    // 舱盖
    const openTarget = phase === 'OPENING' ? easeInOut(u)
      : phase === 'TAKEOFF' || phase === 'PATROL' || phase === 'DESCEND' ? 1
      : phase === 'CLOSING' ? 1 - easeInOut(u) : 0
    openT += (openTarget - openT) * Math.min(1, dt * 7)
    doorL.position.x = -openT * 2.25
    doorR.position.x = openT * 2.25
    padGlow.material.opacity = openT * (0.4 + Math.sin(t * 3) * 0.08)
    dockGlowLight.intensity = 8 + openT * 22

    // 旋翼
    const rotorTarget = phase === 'TAKEOFF' ? u : phase === 'PATROL' ? 1 : phase === 'DESCEND' ? 1 - u * 0.7 : 0
    rotorSpeed += (rotorTarget - rotorSpeed) * Math.min(1, dt * 3)
    for (const r of rotors) r.rotation.y += rotorSpeed * dt * 60
    for (const m of rotorDiscMats) m.opacity = 0.05 + rotorSpeed * 0.12

    // 无人机位置与姿态
    prevPos.copy(drone.position)
    let scanning = false
    if (phase === 'PATROL') {
      const p = curve.getPointAt(u)
      vAhead.copy(curve.getPointAt(Math.min(u + 0.012, 1)))
      drone.position.copy(p)
      drone.lookAt(vAhead)
      curve.getTangentAt(Math.min(u, 0.98), vT0)
      curve.getTangentAt(Math.min(u + 0.02, 1), vT1)
      const turn = vT0.x * vT1.z - vT0.z * vT1.x
      bankAngle += (clamp(-turn * 30, -0.5, 0.5) - bankAngle) * Math.min(1, dt * 4)
      bank.rotation.z = bankAngle
      bank.position.y = Math.sin(t * 2.0) * 0.12
      battery = 98 - u * 34
      scanning = true
    } else {
      bank.position.y = 0
      bankAngle *= 1 - Math.min(1, dt * 5)
      bank.rotation.z = bankAngle
      if (phase === 'TAKEOFF') {
        const e = easeInOut(u)
        drone.position.set(0, PARK_Y + (HOVER_Y - PARK_Y) * e, 0)
        vAhead.copy(curve.getTangentAt(0)).multiplyScalar(6).add(drone.position)
        drone.lookAt(vAhead)
      } else if (phase === 'DESCEND') {
        const e = easeInOut(u)
        drone.position.set(0, HOVER_Y - (HOVER_Y - PARK_Y) * e, 0)
        vTmp.set(0, 0, 6).add(drone.position)   // 朝 +z 水平,自然改出俯角
        drone.lookAt(vTmp)
        battery = 64 + e * 34
      } else {
        drone.position.set(0, PARK_Y, 0)
        battery = 98
      }
    }

    // 扫描光锥 / 瞄准环 / 假投影
    scanCone.visible = scanning
    reticle.visible = scanning
    coneMat.opacity = scanning ? 0.07 + Math.sin(t * 5) * 0.03 : 0
    if (scanning) {
      reticle.position.x = drone.position.x
      reticle.position.z = drone.position.z
      const s = 1 + Math.sin(t * 3.2) * 0.06
      reticle.scale.setScalar(s)
    }
    shadow.position.x = drone.position.x
    shadow.position.z = drone.position.z
    const alt = drone.position.y - PARK_Y
    shadowMat.opacity = 0.35 * clamp(2.2 / (alt + 1), 0.12, 1)
    shadow.scale.setScalar(1 + alt * 0.055)

    // 航行灯 / 频闪 / 信标 / 追逐灯
    navL.material.opacity = 0.65 + Math.sin(t * 4) * 0.3
    navR.material.opacity = 0.65 + Math.cos(t * 4) * 0.3
    const st = t % 1.3
    strobeMat.opacity = (st < 0.06 || (st > 0.14 && st < 0.2)) ? 1 : 0.1
    const blink = Math.sin(t * 4.2) > 0.2
    beaconMat.opacity = blink ? 1 : 0.15
    beaconGlow.material.opacity = blink ? 0.85 : 0.1
    chase.forEach((m, i) => { m.opacity = 0.18 + 0.6 * Math.max(0, Math.sin(t * 2.4 - i * 0.55)) })

    // 扫描脉冲环
    for (const p of pulses) {
      const k = ((t + p.offset) % 4) / 4
      p.mesh.scale.setScalar(1 + k * 6.5)
      p.mat.opacity = (1 - k) * 0.3
    }

    // 航点标记:当前途经的放大提亮
    wpMarks.forEach((m, i) => {
      const active = phase === 'PATROL' && Math.abs(u * (WPTS.length - 1) - (i + 1)) < 0.5
      m.rotation.y += dt * (active ? 3 : 0.8)
      m.scale.setScalar(active ? 1.7 : 1)
      m.material.opacity = active ? 1 : 0.45
    })

    // 粒子
    stars.rotation.y += dt * 0.006
    const arr = moteGeo.attributes.position.array
    for (let i = 0; i < moteCount; i++) {
      arr[i * 3 + 1] += moteSpeed[i] * dt
      if (arr[i * 3 + 1] > 11) arr[i * 3 + 1] = 0.5
    }
    moteGeo.attributes.position.needsUpdate = true

    // 相机:慢摇摆 + 鼠标视差(平滑跟随)
    mouse.x += (mouse.tx - mouse.x) * Math.min(1, dt * 3)
    mouse.y += (mouse.ty - mouse.y) * Math.min(1, dt * 3)
    camPos.set(
      17.5 + Math.sin(t * 0.11) * 1.7 + mouse.x * 2.6,
      10.5 + Math.sin(t * 0.07) * 0.8 - mouse.y * 1.6,
      21.5 + Math.cos(t * 0.09) * 1.2
    )
    camera.position.lerp(camPos, Math.min(1, dt * 2.2))
    camera.lookAt(lookTarget)

    renderer.render(scene, camera)

    // 遥测 ~4Hz
    teleAcc += dt
    if (teleAcc > 0.25 && onTelemetry) {
      teleAcc = 0
      const d = drone.position.distanceTo(prevPos)
      spd += ((d / Math.max(dt, 1e-4)) - spd) * 0.4
      const seg = phase === 'PATROL' ? clamp(Math.floor(u * (WPTS.length - 1)), 0, WPTS.length - 1) : 0
      onTelemetry({
        phase,
        alt: (drone.position.y * 9).toFixed(1),
        spd: spd.toFixed(1),
        bat: Math.round(battery),
        wp: phase === 'PATROL' ? `${clamp(seg, 0, wpTotal)}/${wpTotal}` : `0/${wpTotal}`,
        progress: phase === 'PATROL' ? Math.round(u * 100) : 0
      })
    }
  }
  tick()

  return {
    dispose() {
      cancelAnimationFrame(rafId)
      window.removeEventListener('mousemove', onMouse)
      window.removeEventListener('resize', onResize)
      scene.traverse((o) => {
        if (o.geometry) o.geometry.dispose()
        if (o.material) {
          const mats = Array.isArray(o.material) ? o.material : [o.material]
          for (const m of mats) {
            if (m.map) m.map.dispose()
            m.dispose()
          }
        }
      })
      glowTex.dispose()
      renderer.dispose()
      if (renderer.domElement.parentNode === container) container.removeChild(renderer.domElement)
    }
  }
}
