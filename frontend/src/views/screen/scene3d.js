/**
 * 大屏共享 Three.js 微引擎:地形底座 + 经纬投影 + 常用科技构件。
 *
 * 复刻 loginScene.js 的模式:工厂函数返回带 dispose 的 api;画布透明叠在
 * 面板 CSS 渐变上;WebGL 不可用时构造抛错由 Scene3D.vue 降级为占位。
 * 所有动态构件登记在 internalGroups,clearDynamic() 一次清空(切屏/刷数据用)。
 */
import * as THREE from 'three'

const CYAN = 0x38bdf8
const SKY = 0x7fd4ff
const EDGE = 0x4cc3ff

const RISK_COLORS = { LOW: '#98a2b3', MEDIUM: '#38bdf8', HIGH: '#fbbf24', EXTREME: '#f87171' }

const easeInOut = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2)

/** 径向渐变光斑贴图(按颜色缓存;染色的底纹会发暗,必须以目标色为基色重画) */
const glowCache = new Map()
function glowTexture(color = '#7dd2fc') {
  if (glowCache.has(color)) return glowCache.get(color)
  const c = document.createElement('canvas')
  c.width = c.height = 128
  const ctx = c.getContext('2d')
  const rgb = new THREE.Color(color).toArray().map((v) => Math.round(v * 255))
  const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 64)
  g.addColorStop(0, `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, 0.9)`)
  g.addColorStop(0.4, `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, 0.32)`)
  g.addColorStop(1, `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, 0)`)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, 128, 128)
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.userData.cached = true // 场景 dispose 时跳过(跨屏共享)
  glowCache.set(color, tex)
  return tex
}

/** 文本标签贴图(白字 + 同色辉光描边) */
function labelSprite(text, color = '#7dd2fc') {
  const c = document.createElement('canvas')
  c.width = 256
  c.height = 64
  const ctx = c.getContext('2d')
  ctx.font = '600 26px system-ui, "Microsoft YaHei", sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.shadowColor = color
  ctx.shadowBlur = 12
  ctx.fillStyle = color
  ctx.fillText(String(text).slice(0, 8), 128, 32)
  ctx.shadowBlur = 0
  ctx.fillStyle = 'rgba(238, 248, 255, .96)'
  ctx.fillText(String(text).slice(0, 8), 128, 32)
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false }))
  sp.scale.set(3.4, 0.85, 1)
  return sp
}

/**
 * @param {HTMLElement} container
 * @param {{orbitSpeed?:number, radius?:number, height?:number, fov?:number}} opts
 */
export function createScene3D(container, opts = {}) {
  const width = container.clientWidth || 800
  const height = container.clientHeight || 500

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setClearColor(0x000000, 0)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.75))
  renderer.setSize(width, height)
  container.appendChild(renderer.domElement)

  const scene = new THREE.Scene()
  scene.fog = new THREE.Fog(0x04102a, 80, 190)
  const camera = new THREE.PerspectiveCamera(opts.fov ?? 42, width / height, 0.1, 400)

  /* ---------- 灯光 ---------- */
  scene.add(new THREE.HemisphereLight(0x9fd0ff, 0x0a1e42, 1.2))
  const keyLight = new THREE.DirectionalLight(0xbfd9ff, 2.0)
  keyLight.position.set(-16, 26, 14)
  scene.add(keyLight)

  /* ---------- 地形底座:暗蓝圆盘 + 网格 + 同心技术环 + 星尘 ---------- */
  const baseGroup = new THREE.Group()
  scene.add(baseGroup)

  const ground = new THREE.Mesh(
    new THREE.CircleGeometry(52, 72),
    new THREE.MeshBasicMaterial({ color: 0x04173a, transparent: true, opacity: 0.6 })
  )
  ground.rotation.x = -Math.PI / 2
  baseGroup.add(ground)

  const grid = new THREE.GridHelper(100, 50, 0x9fd0ff, 0x2c62b8)
  grid.material.transparent = true
  grid.material.opacity = 0.18
  grid.position.y = 0.02
  baseGroup.add(grid)

  const techRings = []
  for (const [r, op] of [[14, 0.3], [24, 0.2], [34, 0.14], [45, 0.09]]) {
    const ring = new THREE.Mesh(
      new THREE.RingGeometry(r, r + 0.1, 96),
      new THREE.MeshBasicMaterial({ color: 0x67b7ff, transparent: true, opacity: op, side: THREE.DoubleSide })
    )
    ring.rotation.x = -Math.PI / 2
    ring.position.y = 0.04
    baseGroup.add(ring)
    techRings.push(ring)
  }
  // 外圈缓慢旋转的刻度环(虚线圆)
  const tickRing = new THREE.Mesh(
    new THREE.RingGeometry(49, 49.6, 128, 1, 0, Math.PI * 1.7),
    new THREE.MeshBasicMaterial({ color: CYAN, transparent: true, opacity: 0.35, side: THREE.DoubleSide })
  )
  tickRing.rotation.x = -Math.PI / 2
  tickRing.position.y = 0.06
  baseGroup.add(tickRing)

  const starCount = 260
  const starGeo = new THREE.BufferGeometry()
  const starPos = new Float32Array(starCount * 3)
  for (let i = 0; i < starCount; i++) {
    starPos[i * 3] = (Math.random() - 0.5) * 140
    starPos[i * 3 + 1] = Math.random() * 60 + 6
    starPos[i * 3 + 2] = (Math.random() - 0.5) * 140
  }
  starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3))
  const stars = new THREE.Points(starGeo, new THREE.PointsMaterial({
    color: 0x9fd0ff, size: 0.22, transparent: true, opacity: 0.55, depthWrite: false
  }))
  scene.add(stars)

  /* ---------- 经纬 → 场景投影(包围盒自适应) ---------- */
  const proj = { ready: false, x: null, z: null, hScale: 0.06 }
  /**
   * 建立投影:传入本屏所有坐标(裸经纬度),水平归一到 ±33(占底座约 2/3),
   * 高度按最大航高归一到 13(适度夸张,让航线弧的爬升形态肉眼可见)
   */
  function setProjection(lngLats, maxAlt = 120) {
    let minLng = Infinity, maxLng = -Infinity, minLat = Infinity, maxLat = -Infinity
    lngLats.forEach((p) => {
      minLng = Math.min(minLng, p.lng); maxLng = Math.max(maxLng, p.lng)
      minLat = Math.min(minLat, p.lat); maxLat = Math.max(maxLat, p.lat)
    })
    if (!isFinite(minLng) || maxLng - minLng < 1e-4) { minLng -= 5e-4; maxLng += 5e-4 }
    if (!isFinite(minLat) || maxLat - minLat < 1e-4) { minLat -= 5e-4; maxLat += 5e-4 }
    // 经度按中纬 cos 折算米,再等比缩放
    const midLat = ((minLat + maxLat) / 2) * Math.PI / 180
    const kx = 111320 * Math.cos(midLat)
    const spanX = (maxLng - minLng) * kx
    const spanZ = (maxLat - minLat) * 111320
    const scale = 66 / Math.max(spanX, spanZ, 1)
    const offX = -((minLng + maxLng) / 2)
    const offZ = -((minLat + maxLat) / 2)
    proj.x = (lng) => (lng + offX) * kx * scale
    proj.z = (lat) => -((lat + offZ) * 111320 * scale)
    proj.hScale = 13 / Math.max(maxAlt, 10)
    proj.ready = true
  }
  const toVec3 = (p, fallbackH = 0) => new THREE.Vector3(
    proj.x(p.lng), (p.h ?? fallbackH) * proj.hScale, proj.z(p.lat))

  /* ---------- 动态构件登记 ---------- */
  const dynamic = new THREE.Group()
  scene.add(dynamic)
  const updaters = []

  /** 辉光光柱标记:地面光环 + 竖直光柱 + 顶端辉光 + 可选标签 */
  function addMarker(p, { color = '#38bdf8', label = '', size = 1, beam = 3.2, pulse = true } = {}) {
    const g = new THREE.Group()
    g.position.copy(toVec3(p, 0))
    dynamic.add(g)
    const col = new THREE.Color(color)

    const halo = new THREE.Mesh(
      new THREE.RingGeometry(0.65 * size, 0.85 * size, 40),
      new THREE.MeshBasicMaterial({ color: col, transparent: true, opacity: 0.6, side: THREE.DoubleSide })
    )
    halo.rotation.x = -Math.PI / 2
    halo.position.y = 0.05
    g.add(halo)

    const beamMat = new THREE.MeshBasicMaterial({
      color: col, transparent: true, opacity: 0.4, depthWrite: false, blending: THREE.AdditiveBlending
    })
    const beamMesh = new THREE.Mesh(new THREE.CylinderGeometry(0.09 * size, 0.16 * size, beam, 12, 1, true), beamMat)
    beamMesh.position.y = beam / 2
    g.add(beamMesh)

    const glow = new THREE.Sprite(new THREE.SpriteMaterial({
      map: glowTexture(color), color: 0xffffff, transparent: true, opacity: 0.9, depthWrite: false
    }))
    glow.scale.setScalar(1.6 * size)
    glow.position.y = beam
    g.add(glow)

    if (label) {
      const sp = labelSprite(label, color)
      sp.position.y = beam + 0.7
      g.add(sp)
    }
    if (pulse) {
      const ripple = new THREE.Mesh(
        new THREE.RingGeometry(0.9 * size, 1.0 * size, 48),
        new THREE.MeshBasicMaterial({ color: col, transparent: true, opacity: 0.5, side: THREE.DoubleSide })
      )
      ripple.rotation.x = -Math.PI / 2
      ripple.position.y = 0.06
      g.add(ripple)
      updaters.push((dt, t) => {
        const k = (t * 0.5 + g.id * 0.13) % 1
        ripple.scale.setScalar(1 + k * 2.6)
        ripple.material.opacity = (1 - k) * 0.5
      })
    }
    return {
      group: g,
      setPos(p2) { g.position.copy(toVec3(p2, 0)) },
      dispose() { dynamic.remove(g); g.traverse((o) => { o.geometry?.dispose(); o.material?.dispose?.() }) }
    }
  }

  /** 电子围栏:半透明圆柱薄壁 + 顶部亮环 */
  function addFence(p, radiusM, { color = '#f87171', height = 1.8, opacity = 0.14 } = {}) {
    if (!proj.ready) return null
    const c = toVec3(p, 0)
    // 半径米 → 场景单位:取投影 scale(44/跨度米)
    const midLat = p.lat * Math.PI / 180
    const rMetersX = radiusM / (111320 * Math.cos(midLat))
    const edge = new THREE.Vector3(proj.x(p.lng + rMetersX), 0, proj.z(p.lat))
    const r = Math.abs(edge.x - c.x) || 0.5
    const col = new THREE.Color(color)
    const g = new THREE.Group()
    g.position.copy(c)
    dynamic.add(g)

    const wall = new THREE.Mesh(
      new THREE.CylinderGeometry(r, r, height, 48, 1, true),
      new THREE.MeshBasicMaterial({ color: col, transparent: true, opacity, side: THREE.DoubleSide, depthWrite: false })
    )
    wall.position.y = height / 2
    g.add(wall)
    const top = new THREE.Mesh(
      new THREE.TorusGeometry(r, 0.035, 8, 72),
      new THREE.MeshBasicMaterial({ color: col, transparent: true, opacity: 0.85 })
    )
    top.rotation.x = Math.PI / 2
    top.position.y = height
    g.add(top)
    const glow = new THREE.Sprite(new THREE.SpriteMaterial({
      map: glowTexture(color), transparent: true, opacity: 0.5, depthWrite: false
    }))
    glow.scale.setScalar(r * 0.9)
    glow.position.y = 0.1
    g.add(glow)
    return g
  }

  /** 3D 航线:CatmullRom 弧线 + 航点光标 + 沿线巡航光点 */
  function addRoute(points, { color = '#7dd2fc', cruise = true, radius = 0.05 } = {}) {
    if (!proj.ready || points.length < 2) return null
    const curve = new THREE.CatmullRomCurve3(points.map((p) => toVec3(p)))
    const g = new THREE.Group()
    dynamic.add(g)

    const tube = new THREE.Mesh(
      new THREE.TubeGeometry(curve, Math.max(60, points.length * 16), radius, 8, false),
      new THREE.MeshBasicMaterial({ color: new THREE.Color(color), transparent: true, opacity: 0.85 })
    )
    g.add(tube)
    // 辉光副线(更粗、更淡)
    const glowTube = new THREE.Mesh(
      new THREE.TubeGeometry(curve, Math.max(60, points.length * 16), radius * 3, 8, false),
      new THREE.MeshBasicMaterial({
        color: new THREE.Color(color), transparent: true, opacity: 0.12, depthWrite: false, blending: THREE.AdditiveBlending
      })
    )
    g.add(glowTube)

    const wpts = points.map((p) => {
      const v = toVec3(p)
      const m = new THREE.Mesh(
        new THREE.OctahedronGeometry(0.3),
        new THREE.MeshBasicMaterial({ color: new THREE.Color(color), transparent: true, opacity: 0.9 })
      )
      m.position.copy(v)
      g.add(m)
      const stem = new THREE.Mesh(
        new THREE.CylinderGeometry(0.014, 0.014, v.y, 4),
        new THREE.MeshBasicMaterial({ color: new THREE.Color(color), transparent: true, opacity: 0.35 })
      )
      stem.position.set(v.x, v.y / 2, v.z)
      g.add(stem)
      return m
    })

    let cruiseDot = null
    if (cruise) {
      cruiseDot = new THREE.Group()
      const dot = new THREE.Mesh(
        new THREE.SphereGeometry(0.2, 14, 14),
        new THREE.MeshBasicMaterial({ color: 0xffffff })
      )
      const halo = new THREE.Sprite(new THREE.SpriteMaterial({
        map: glowTexture(color), transparent: true, opacity: 0.95, depthWrite: false
      }))
      halo.scale.setScalar(1.6)
      cruiseDot.add(dot, halo)
      g.add(cruiseDot)
      const t0 = Math.random()
      updaters.push((dt, t) => {
        const k = (t0 + t * 0.012) % 1
        const pos = curve.getPointAt(k)
        cruiseDot.position.copy(pos)
        wpts.forEach((m, i) => {
          const near = Math.abs(k * (points.length - 1) - i) < 0.5
          m.scale.setScalar(near ? 1.8 : 1)
          m.rotation.y += dt * (near ? 4 : 1)
        })
      })
    } else {
      updaters.push((dt) => wpts.forEach((m) => { m.rotation.y += dt * 1.2 }))
    }
    return { group: g, curve, cruiseDot }
  }

  /** 轨迹回放:淡色全轨迹 + 亮色已飞段(drawRange)+ 当前帧无人机,api.setProgress(0..1) */
  function addTrack(points, { color = '#38bdf8', drone = buildDroneIcon } = {}) {
    if (!proj.ready || points.length < 2) return null
    const pts = points.map((p) => toVec3(p))
    const g = new THREE.Group()
    dynamic.add(g)

    const allGeo = new THREE.BufferGeometry().setFromPoints(pts)
    g.add(new THREE.Line(allGeo, new THREE.LineBasicMaterial({
      color: 0x64748b, transparent: true, opacity: 0.5
    })))

    const flownGeo = new THREE.BufferGeometry().setFromPoints(pts)
    const flown = new THREE.Line(flownGeo, new THREE.LineBasicMaterial({
      color: new THREE.Color(color), transparent: true, opacity: 1
    }))
    g.add(flown)

    const cur = drone()
    cur.scale.setScalar(1.6)
    g.add(cur)
    // 当前帧辉光 + 到地面的垂线(强化"悬停高度"的空间感)
    const curGlow = new THREE.Sprite(new THREE.SpriteMaterial({
      map: glowTexture(color), transparent: true, opacity: 0.85, depthWrite: false
    }))
    curGlow.scale.setScalar(1.5)
    g.add(curGlow)
    const dropGeo = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(), new THREE.Vector3(0, 1, 0)])
    const drop = new THREE.Line(dropGeo, new THREE.LineBasicMaterial({
      color: new THREE.Color(color), transparent: true, opacity: 0.3
    }))
    g.add(drop)
    updaters.push((dt) => { cur.rotation.y += dt * 8 })

    const api = {
      group: g,
      setProgress(k) {
        const n = Math.max(2, Math.round(Math.max(0, Math.min(1, k)) * (pts.length - 1)) + 1)
        flownGeo.setDrawRange(0, n)
        const idx = Math.min(pts.length - 1, n - 1)
        cur.position.copy(pts[idx])
        curGlow.position.copy(pts[idx])
        dropGeo.setFromPoints([pts[idx], new THREE.Vector3(pts[idx].x, 0, pts[idx].z)])
      }
    }
    api.setProgress(1)
    return api
  }

  /** 简版无人机图标(轨迹当前帧/巡航用) */
  function buildDroneIcon() {
    const g = new THREE.Group()
    const mat = new THREE.MeshBasicMaterial({ color: 0x7dd2fc })
    const body = new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.1, 0.34), mat)
    g.add(body)
    for (const [x, z] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) {
      const arm = new THREE.Mesh(new THREE.BoxGeometry(0.22, 0.03, 0.05), mat)
      arm.position.set(x * 0.17, 0, z * 0.17)
      arm.rotation.y = Math.atan2(z, x)
      g.add(arm)
      const ring = new THREE.Mesh(
        new THREE.TorusGeometry(0.07, 0.012, 6, 18),
        new THREE.MeshBasicMaterial({ color: SKY, transparent: true, opacity: 0.55 })
      )
      ring.rotation.x = Math.PI / 2
      ring.position.set(x * 0.3, 0.02, z * 0.3)
      g.add(ring)
    }
    return g
  }

  /** 设备孪生:程序化无人机(机身/鼻锥/GPS 罩/云台/起落架/航行灯 + 双层桨叶旋翼) */
  function addDroneModel({ scale = 1 } = {}) {
    const g = new THREE.Group()
    dynamic.add(g)
    const shell = new THREE.MeshStandardMaterial({
      color: 0x0f2f5e, metalness: 0.6, roughness: 0.35,
      emissive: 0x1550c8, emissiveIntensity: 0.55
    })
    const darkShell = new THREE.MeshStandardMaterial({ color: 0x08152e, metalness: 0.8, roughness: 0.3 })
    const body = new THREE.Mesh(new THREE.BoxGeometry(0.72, 0.22, 0.72), shell)
    const nose = new THREE.Mesh(new THREE.BoxGeometry(0.4, 0.14, 0.32), shell)
    nose.position.set(0, 0.02, 0.44)
    const top = new THREE.Mesh(new THREE.BoxGeometry(0.36, 0.14, 0.36), shell)
    top.position.y = 0.17
    const gps = new THREE.Mesh(new THREE.SphereGeometry(0.09, 12, 12), darkShell)
    gps.position.y = 0.3
    const gimbal = new THREE.Mesh(new THREE.SphereGeometry(0.11, 12, 12), darkShell)
    gimbal.position.set(0, -0.15, 0.32)
    const belly = new THREE.PointLight(CYAN, 3.2, 4, 2)
    belly.position.y = -0.2
    g.add(body, nose, top, gps, gimbal, belly)

    // 起落架:双支柱 + 双滑橇
    const armMat = new THREE.MeshStandardMaterial({ color: 0x1a4275, metalness: 0.6, roughness: 0.4 })
    for (const dz of [-0.28, 0.28]) {
      for (const dx of [-0.3, 0.3]) {
        const strut = new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.3, 0.05), armMat)
        strut.position.set(dx, -0.24, dz)
        g.add(strut)
      }
      const skid = new THREE.Mesh(new THREE.BoxGeometry(0.78, 0.05, 0.07), armMat)
      skid.position.set(0, -0.4, dz)
      g.add(skid)
    }
    // 前臂航行灯(左红右绿)
    const navL = new THREE.Mesh(new THREE.SphereGeometry(0.035, 8, 8), new THREE.MeshBasicMaterial({ color: 0xff4d6d }))
    navL.position.set(-0.5, 0.07, 0.5)
    const navR = new THREE.Mesh(new THREE.SphereGeometry(0.035, 8, 8), new THREE.MeshBasicMaterial({ color: 0x4ade80 }))
    navR.position.set(0.5, 0.07, 0.5)
    g.add(navL, navR)

    const rotors = []
    for (const [x, z] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) {
      const arm = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.05, 0.08), armMat)
      arm.position.set(x * 0.34, 0.05, z * 0.34)
      arm.rotation.y = Math.atan2(z, x)
      g.add(arm)
      const motor = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.07, 0.1, 10), shell)
      motor.position.set(x * 0.56, 0.1, z * 0.56)
      g.add(motor)
      const spin = new THREE.Group()
      spin.position.set(x * 0.56, 0.17, z * 0.56)
      const bladeMat = new THREE.MeshBasicMaterial({ color: 0x9fd0ff, transparent: true, opacity: 0.7 })
      const b1 = new THREE.Mesh(new THREE.BoxGeometry(0.66, 0.012, 0.05), bladeMat)
      const b2 = new THREE.Mesh(new THREE.BoxGeometry(0.66, 0.012, 0.05), bladeMat)
      b2.rotation.y = Math.PI / 2
      const discMat = new THREE.MeshBasicMaterial({
        color: SKY, transparent: true, opacity: 0.08, side: THREE.DoubleSide, depthWrite: false
      })
      const disc = new THREE.Mesh(new THREE.CircleGeometry(0.36, 24), discMat)
      disc.rotation.x = -Math.PI / 2
      spin.add(b1, b2, disc)
      g.add(spin)
      rotors.push({ spin, discMat })
    }
    g.scale.setScalar(scale)
    let rotorSpeed = 0
    updaters.push((dt) => {
      for (const r of rotors) {
        r.spin.rotation.y += rotorSpeed * dt * 70
        r.discMat.opacity = 0.06 + rotorSpeed * 0.16
      }
    })
    return {
      group: g,
      setPose({ heading = 0, rotor = 1, tilt = 0, pos } = {}) {
        g.rotation.y = -heading * Math.PI / 180
        g.rotation.z = tilt * Math.PI / 180
        rotorSpeed += (rotor - rotorSpeed) * Math.min(1, dt0 * 3)
        if (pos) g.position.copy(pos)
      },
      setRotor(v) { rotorSpeed = v }
    }
  }

  /** 设备孪生:机场(方形舱体 + 双页滑开舱盖 + LED 灯条),api.setCover(open) */
  function addDockModel({ scale = 1 } = {}) {
    const g = new THREE.Group()
    dynamic.add(g)
    const metal = new THREE.MeshStandardMaterial({ color: 0x112b52, metalness: 0.55, roughness: 0.45 })
    const metalLight = new THREE.MeshStandardMaterial({ color: 0x1a4275, metalness: 0.5, roughness: 0.4 })

    // 混凝土基座 + 主舱体(方形,贴近 DJI 机场形态)
    const plinth = new THREE.Mesh(new THREE.BoxGeometry(3.7, 0.26, 3.7), metal)
    plinth.position.y = 0.13
    const body = new THREE.Mesh(new THREE.BoxGeometry(3.0, 0.92, 3.0), metalLight)
    body.position.y = 0.72
    g.add(plinth, body)

    // 舱体棱线描边 + 顶部停机坪(舱盖打开后可见)
    const edges = new THREE.LineSegments(
      new THREE.EdgesGeometry(body.geometry),
      new THREE.LineBasicMaterial({ color: EDGE, transparent: true, opacity: 0.75 })
    )
    edges.position.copy(body.position)
    g.add(edges)
    const pad = new THREE.Mesh(
      new THREE.BoxGeometry(2.55, 0.05, 2.55),
      new THREE.MeshBasicMaterial({ color: 0x0e3a70 })
    )
    pad.position.y = 1.2
    g.add(pad)
    const padRing = new THREE.Mesh(
      new THREE.RingGeometry(0.78, 0.87, 40),
      new THREE.MeshBasicMaterial({ color: CYAN, transparent: true, opacity: 0.6, side: THREE.DoubleSide })
    )
    padRing.rotation.x = -Math.PI / 2
    padRing.position.y = 1.24
    g.add(padRing)

    // 双页滑动舱盖
    const doorMat = new THREE.MeshStandardMaterial({ color: 0x21497f, metalness: 0.5, roughness: 0.38 })
    const doorL = new THREE.Mesh(new THREE.BoxGeometry(1.48, 0.09, 2.96), doorMat)
    const doorR = new THREE.Mesh(new THREE.BoxGeometry(1.48, 0.09, 2.96), doorMat)
    doorL.position.set(-0.75, 1.31, 0)
    doorR.position.set(0.75, 1.31, 0)
    g.add(doorL, doorR)

    // 舱体示廓灯条(呼吸)
    const ledMat = new THREE.MeshBasicMaterial({ color: EDGE, transparent: true, opacity: 0.85 })
    for (const dz of [-1.04, 1.04]) {
      const led = new THREE.Mesh(new THREE.BoxGeometry(2.92, 0.045, 0.05), ledMat)
      led.position.set(0, 0.28, dz)
      g.add(led)
    }

    // 风杆 + 航标灯
    const mast = new THREE.Mesh(new THREE.CylinderGeometry(0.03, 0.03, 0.9, 8), metal)
    mast.position.set(1.58, 1.35, -1.58)
    g.add(mast)
    const beacon = new THREE.Mesh(
      new THREE.SphereGeometry(0.08, 10, 10),
      new THREE.MeshBasicMaterial({ color: 0xff5470, transparent: true })
    )
    beacon.position.set(1.58, 1.84, -1.58)
    g.add(beacon)
    const beaconGlow = new THREE.Sprite(new THREE.SpriteMaterial({
      map: glowTexture('#ff7a90'), transparent: true, opacity: 0.75, depthWrite: false
    }))
    beaconGlow.scale.setScalar(0.95)
    beaconGlow.position.copy(beacon.position)
    g.add(beaconGlow)

    g.scale.setScalar(scale)
    let openK = 0
    let coverOpen = false
    updaters.push((dt, t) => {
      beacon.material.opacity = 0.5 + Math.sin(t * 3) * 0.4
      ledMat.opacity = 0.55 + Math.sin(t * 2.2) * 0.35
      openK += ((coverOpen ? 1 : 0) - openK) * Math.min(1, dt * 2)
      doorL.position.x = -(0.75 + openK * 1.5)
      doorR.position.x = 0.75 + openK * 1.5
    })
    return {
      group: g,
      setCover(open) { coverOpen = !!open }
    }
  }

  /** 臭气烟羽:沿扩散轨迹的漂移粒子,浓度渐变色 */
  function addPlume(points, { colorA = '#fde68a', colorB = '#f87171' } = {}) {
    if (!proj.ready || points.length < 2) return null
    const g = new THREE.Group()
    dynamic.add(g)
    const anchors = points.map((p) => toVec3(p, 0))
    // 相邻锚点方向作为漂移风向
    const segs = []
    for (let i = 1; i < anchors.length; i++) {
      segs.push({ from: anchors[i - 1], to: anchors[i], len: anchors[i - 1].distanceTo(anchors[i]) })
    }
    const total = segs.reduce((s, x) => s + x.len, 0) || 1
    const N = 420
    const geo = new THREE.BufferGeometry()
    const pos = new Float32Array(N * 3)
    const colAttr = new Float32Array(N * 3)
    const life = new Float32Array(N)
    const ca = new THREE.Color(colorA)
    const cb = new THREE.Color(colorB)
    const tmp = new THREE.Color()
    for (let i = 0; i < N; i++) { life[i] = Math.random(); spawn(i, Math.random()) }
    function spawn(i, k) {
      // 按距离比例落在烟羽轴线上,带横向随机扩散
      let d = k * total
      let seg = segs[0]
      for (const s of segs) { if (d <= s.len) { seg = s; break } d -= s.len }
      const u = seg.len ? Math.max(0, Math.min(1, d / seg.len)) : 0
      pos[i * 3] = seg.from.x + (seg.to.x - seg.from.x) * u + (Math.random() - 0.5) * (0.5 + u * 2.4)
      pos[i * 3 + 1] = 0.35 + Math.random() * (0.6 + u * 1.6)
      pos[i * 3 + 2] = seg.from.z + (seg.to.z - seg.from.z) * u + (Math.random() - 0.5) * (0.5 + u * 2.4)
      tmp.lerpColors(ca, cb, u)
      colAttr[i * 3] = tmp.r; colAttr[i * 3 + 1] = tmp.g; colAttr[i * 3 + 2] = tmp.b
    }
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3))
    geo.setAttribute('color', new THREE.BufferAttribute(colAttr, 3))
    const mat = new THREE.PointsMaterial({
      size: 0.34, vertexColors: true, transparent: true, opacity: 0.5,
      map: glowTexture('#a5f3fc'), depthWrite: false, blending: THREE.AdditiveBlending
    })
    const pts = new THREE.Points(geo, mat)
    g.add(pts)
    updaters.push((dt) => {
      for (let i = 0; i < N; i++) {
        life[i] += dt * 0.12
        if (life[i] > 1) { life[i] = 0; spawn(i, Math.random() * 0.15) }
        pos[i * 3 + 1] += dt * 0.24
        pos[i * 3] += Math.sin(life[i] * 9 + i) * dt * 0.18
      }
      geo.attributes.position.needsUpdate = true
    })
    return g
  }

  /** 发光河道:地形上的一条亮蓝曲线带 */
  function addRiver(points, { color = '#22d3ee' } = {}) {
    if (!proj.ready || points.length < 2) return null
    const vecs = points.map((p) => toVec3(p, 0.02))
    const curve = new THREE.CatmullRomCurve3(vecs)
    const tube = new THREE.Mesh(
      new THREE.TubeGeometry(curve, 90, 0.22, 8, false),
      new THREE.MeshBasicMaterial({
        color: new THREE.Color(color), transparent: true, opacity: 0.32, depthWrite: false
      })
    )
    const edge = new THREE.Mesh(
      new THREE.TubeGeometry(curve, 90, 0.05, 8, false),
      new THREE.MeshBasicMaterial({ color: new THREE.Color(color), transparent: true, opacity: 0.85 })
    )
    dynamic.add(tube, edge)
    return { tube, edge }
  }

  /** 一次清空全部动态构件(切屏刷新时调用) */
  function clearDynamic() {
    for (const u of updaters.splice(0)) { /* 丢弃 updaters */ }
    dynamic.traverse((o) => {
      o.geometry?.dispose()
      const mats = Array.isArray(o.material) ? o.material : (o.material ? [o.material] : [])
      for (const m of mats) m.dispose?.()
    })
    dynamic.clear()
  }

  /* ---------- 相机:自动环绕 + 鼠标视差 + 平滑聚焦 ---------- */
  const lookTarget = new THREE.Vector3(0, 3, 0)
  const lookGoal = lookTarget.clone()
  const camPos = new THREE.Vector3()
  const mouse = { x: 0, y: 0, tx: 0, ty: 0 }
  const orbit = { speed: opts.orbitSpeed ?? 0.07, radius: opts.radius ?? 30, height: opts.height ?? 20 }
  let theta = Math.PI * 0.28

  const onMouse = (e) => {
    const r = container.getBoundingClientRect()
    mouse.tx = ((e.clientX - r.left) / r.width - 0.5) * 2
    mouse.ty = ((e.clientY - r.top) / r.height - 0.5) * 2
  }
  container.addEventListener('mousemove', onMouse)

  const ro = new ResizeObserver(() => {
    const w = container.clientWidth || 1, h = container.clientHeight || 1
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    renderer.setSize(w, h)
  })
  ro.observe(container)

  function focus(p) {
    if (p && proj.ready) lookGoal.copy(toVec3(p, 3))
    else lookGoal.set(0, 3, 0)
  }

  /* ---------- 动画循环 ---------- */
  const clock = new THREE.Clock()
  let rafId = 0
  let dt0 = 0.016
  let paused = false

  function tick() {
    rafId = requestAnimationFrame(tick)
    const dt = Math.min(clock.getDelta(), 0.05)
    if (paused) return
    dt0 = dt
    const t = clock.elapsedTime

    theta += dt * orbit.speed
    mouse.x += (mouse.tx - mouse.x) * Math.min(1, dt * 3)
    mouse.y += (mouse.ty - mouse.y) * Math.min(1, dt * 3)
    camPos.set(
      Math.sin(theta) * orbit.radius + mouse.x * 2.4,
      orbit.height - mouse.y * 2.2,
      Math.cos(theta) * orbit.radius
    )
    camera.position.lerp(camPos, Math.min(1, dt * 2.4))
    lookTarget.lerp(lookGoal, Math.min(1, dt * 2))
    camera.lookAt(lookTarget)

    tickRing.rotation.z += dt * 0.06
    techRings[3].rotation.z -= dt * 0.02
    stars.rotation.y += dt * 0.005

    for (const u of updaters) u(dt, t)
    renderer.render(scene, camera)
  }
  tick()

  return {
    scene, camera, renderer,
    setProjection,
    addMarker, addFence, addRoute, addTrack, addPlume, addRiver,
    addDroneModel, addDockModel, buildDroneIcon,
    clearDynamic, focus,
    setOrbit({ speed, radius, height } = {}) {
      if (speed !== undefined) orbit.speed = speed
      if (radius !== undefined) orbit.radius = radius
      if (height !== undefined) orbit.height = height
    },
    pause() { paused = true },
    resume() { paused = false },
    get projectionReady() { return proj.ready },
    dispose() {
      cancelAnimationFrame(rafId)
      ro.disconnect()
      container.removeEventListener('mousemove', onMouse)
      clearDynamic()
      scene.traverse((o) => {
        o.geometry?.dispose()
        const mats = Array.isArray(o.material) ? o.material : (o.material ? [o.material] : [])
        for (const m of mats) {
          // 辉光贴图是模块级缓存,跨场景共享,这里不能释放
          if (m.map && !m.map.userData?.cached) m.map.dispose()
          m.dispose?.()
        }
      })
      renderer.dispose()
      if (renderer.domElement.parentNode === container) container.removeChild(renderer.domElement)
    }
  }
}

export { RISK_COLORS }
