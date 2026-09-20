<template>
  <div class="page" v-loading="loading">
    <!-- 头部 -->
    <div class="page-header">
      <div class="head-left">
        <el-button link @click="$router.push('/docks')">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="page-title">{{ dock?.name || '机场控制' }}</span>
        <el-tag size="small" :type="dictTag(DEVICE_STATUS, dock?.status)" effect="plain">
          {{ dictLabel(DEVICE_STATUS, dock?.status) }}
        </el-tag>
        <span class="sn">{{ dock?.deviceSn }}</span>
        <span class="model" v-if="dock?.deviceModel">{{ dock?.deviceModel }}</span>
      </div>
      <div class="actions">
        <span class="poll-tip"><i :class="{ on: polling }"></i>{{ polling ? '遥测刷新中(3s)' : '已暂停' }}</span>
        <el-button size="small" @click="togglePoll">{{ polling ? '暂停刷新' : '继续刷新' }}</el-button>
        <el-button size="small" type="primary" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <div class="tabs-wrap panel">
      <el-tabs v-model="activeTab" class="dock-tabs">
        <!-- ============ TAB 1 基本信息 ============ -->
        <el-tab-pane label="基本信息" name="info">
          <div class="info-grid">
            <!-- 剖面示意 -->
            <div class="viz-block">
              <div class="block-title">舱体状态</div>
              <svg class="dock-viz" viewBox="0 0 320 250" fill="none">
                <defs>
                  <linearGradient id="dockBody" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" stop-color="#e8f1ff"/><stop offset="1" stop-color="#cfe1fa"/>
                  </linearGradient>
                  <linearGradient id="dockCover" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" stop-color="#f7fbff"/><stop offset="1" stop-color="#d6e6fb"/>
                  </linearGradient>
                </defs>

                <!-- 舱盖:开启时向外翻开 -->
                <g class="cover" :class="{ open: coverOpen }">
                  <path d="M96 96 L160 74 L224 96 L160 118 Z" fill="url(#dockCover)"
                        stroke="#7fa6d8" stroke-width="2"/>
                </g>

                <!-- 舱体 -->
                <path d="M96 96 L96 186 L224 186 L224 96" stroke="#7fa6d8" stroke-width="2" fill="url(#dockBody)"/>
                <path d="M96 96 L160 118 L224 96" stroke="#7fa6d8" stroke-width="2" fill="none"/>

                <!-- 舱内飞行器 -->
                <g class="drone" :class="{ inside: droneInDock }">
                  <rect x="138" y="126" width="44" height="26" rx="8" fill="#ffffff" stroke="#1877e6" stroke-width="2"/>
                  <circle cx="160" cy="139" r="6" fill="#1877e6"/>
                  <circle cx="128" cy="132" r="9" fill="none" stroke="#93c5fd" stroke-width="2"/>
                  <circle cx="192" cy="132" r="9" fill="none" stroke="#93c5fd" stroke-width="2"/>
                  <circle cx="128" cy="146" r="9" fill="none" stroke="#93c5fd" stroke-width="2"/>
                  <circle cx="192" cy="146" r="9" fill="none" stroke="#93c5fd" stroke-width="2"/>
                </g>

                <!-- 推杆:展开时伸出 -->
                <g class="putter" :class="{ open: putterOpen }">
                  <rect x="126" y="176" width="68" height="7" rx="3.5" fill="#7fa6d8"/>
                </g>

                <!-- 地面 -->
                <path d="M40 196 H280" stroke="#c3d6ee" stroke-width="2" stroke-linecap="round"/>
              </svg>

              <div class="viz-legend">
                <span><i :class="{ on: coverOpen }"></i>舱盖 {{ coverText }}</span>
                <span><i :class="{ on: putterOpen }"></i>推杆 {{ putterText }}</span>
                <span><i :class="{ on: droneInDock }"></i>飞行器 {{ droneInDock ? '在舱' : '舱外' }}</span>
              </div>
            </div>

            <!-- 台账 + 指标 -->
            <div class="info-main">
              <div class="block-title">设备台账</div>
              <el-descriptions :column="3" border size="small">
                <el-descriptions-item label="序列号">{{ dock?.deviceSn }}</el-descriptions-item>
                <el-descriptions-item label="机型">{{ dock?.deviceModel || '-' }}</el-descriptions-item>
                <el-descriptions-item label="固件版本">{{ dock?.firmwareVersion || '-' }}</el-descriptions-item>
                <el-descriptions-item label="挂载无人机">
                  {{ dock?.subDeviceCount ? dock.subDeviceCount + ' 台' : '未挂载' }}
                </el-descriptions-item>
                <el-descriptions-item label="最近在线">{{ dock?.lastOnlineAt || '-' }}</el-descriptions-item>
                <el-descriptions-item label="备注">{{ dock?.remark || '-' }}</el-descriptions-item>
              </el-descriptions>

              <div class="block-title">运行指标</div>
              <div class="metrics">
                <div class="metric">
                  <span class="m-label">工作状态</span>
                  <el-tag size="small" :type="modeTag('DOCK', t?.modeCode)" effect="light">
                    {{ t?.modeLabel || '-' }}
                  </el-tag>
                </div>
                <div class="metric">
                  <span class="m-label">电池电量</span>
                  <b class="m-value">{{ t?.batteryPercent ?? '-' }}<i>%</i></b>
                </div>
                <div class="metric">
                  <span class="m-label">环境温度</span>
                  <b class="m-value">{{ t?.environmentTemperature ?? '-' }}<i>℃</i></b>
                </div>
                <div class="metric">
                  <span class="m-label">风速</span>
                  <b class="m-value">{{ t?.windSpeed ?? '-' }}<i>m/s</i></b>
                </div>
                <div class="metric">
                  <span class="m-label">降雨量</span>
                  <b class="m-value">{{ t?.rainfall ?? '-' }}<i>mm</i></b>
                </div>
                <div class="metric">
                  <span class="m-label">媒体文件</span>
                  <b class="m-value">{{ t?.mediaFileCount ?? '-' }}<i>个</i></b>
                </div>
              </div>

              <div class="block-title">充电与网络</div>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="充电状态">
                  <el-tag size="small" :type="charging?.state === 1 ? 'success' : 'info'" effect="plain">
                    {{ charging?.state === 1 ? '充电中' : '未充电' }}
                  </el-tag>
                  <span v-if="charging?.capacity_percent !== undefined" class="kv">
                    {{ charging.capacity_percent }}%
                  </span>
                </el-descriptions-item>
                <el-descriptions-item label="网络制式">{{ network?.type ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="信号质量">{{ network?.quality ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="舱内飞行器">
                  <span v-if="subDevice?.device_sn">
                    <span class="kv">{{ subDevice.device_sn }}</span>
                    <el-tag size="small" :type="subDevice.device_online_status === 1 ? 'success' : 'info'"
                            effect="plain" style="margin-left:6px">
                      {{ subDevice.device_online_status === 1 ? '已开机' : '关机' }}
                    </el-tag>
                    <el-tag size="small" :type="subDevice.device_paired === 1 ? 'success' : 'warning'"
                            effect="plain" style="margin-left:4px">
                      {{ subDevice.device_paired === 1 ? '已配对' : '未配对' }}
                    </el-tag>
                  </span>
                  <span v-else>-</span>
                </el-descriptions-item>
              </el-descriptions>
              <p class="update-time" v-if="t?.updateTime">遥测更新于 {{ t.updateTime }}</p>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ TAB 2 指令单元 ============ -->
        <el-tab-pane :label="`指令单元(${services.length})`" name="commands">
          <div class="cmd-unit-wrap">
            <el-alert v-if="dock?.status !== 'ONLINE'" type="warning" :closable="false"
                      title="机场离线,指令无法下发" class="offline-alert" />

            <!-- 上:机场可视化 + 状态 HUD -->
            <div class="cmd-viz-row">
              <div class="viz-console">
                <svg class="dock-svg" viewBox="0 0 640 460" fill="none">
                  <defs>
                    <linearGradient id="cuBody" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0" stop-color="#eaf3ff"/><stop offset="1" stop-color="#d3e5fa"/>
                    </linearGradient>
                    <linearGradient id="cuCover" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0" stop-color="#60a5fa"/><stop offset="1" stop-color="#2563eb"/>
                    </linearGradient>
                    <linearGradient id="cuBeam" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0" stop-color="rgba(24,119,230,.4)"/><stop offset="1" stop-color="rgba(24,119,230,0)"/>
                    </linearGradient>
                    <pattern id="cuGrid" width="32" height="32" patternUnits="userSpaceOnUse">
                      <path d="M32 0 H0 V32" fill="none" stroke="rgba(24,119,230,.08)" stroke-width="1"/>
                    </pattern>
                  </defs>

                  <rect width="640" height="460" rx="10" fill="#ffffff"/>
                  <rect width="640" height="460" rx="10" fill="url(#cuGrid)"/>

                  <!-- HUD 四角与标头 -->
                  <g stroke="#1877e6" stroke-width="2" opacity=".7">
                    <path d="M14 34 V14 H34"/><path d="M606 14 H626 V34"/>
                    <path d="M626 426 V446 H606"/><path d="M34 446 H14 V426"/>
                  </g>
                  <text x="26" y="34" class="svg-mono svg-dim">DOCK CONSOLE</text>
                  <text x="614" y="34" class="svg-mono svg-cyan" text-anchor="end">SYS · {{ dock?.status === 'ONLINE' ? 'ONLINE' : 'OFFLINE' }}</text>

                  <!-- 扫描线 -->
                  <rect class="scanline" x="16" y="0" width="608" height="2" fill="rgba(24,119,230,.3)"/>

                  <!-- 地面 -->
                  <ellipse cx="320" cy="386" rx="230" ry="22" fill="rgba(24,119,230,.06)"/>
                  <line x1="70" y1="386" x2="570" y2="386" stroke="rgba(24,119,230,.45)" stroke-width="1.5"/>

                  <!-- 补光灯 + 光锥 -->
                  <g class="part" :class="hotClass('part-light')">
                    <line x1="96" y1="386" x2="96" y2="150" stroke="#1877e6" stroke-width="3"/>
                    <rect x="84" y="136" width="26" height="12" rx="3" fill="#1877e6"/>
                    <path d="M97 150 L30 300 L164 300 Z" fill="url(#cuBeam)" class="beam"
                          :class="{ on: lightOn }"/>
                  </g>

                  <!-- 天线 -->
                  <g class="part" :class="hotClass('part-antenna')">
                    <line x1="540" y1="386" x2="540" y2="196" stroke="#1877e6" stroke-width="3"/>
                    <circle cx="540" cy="190" r="5" fill="#1877e6"/>
                    <path d="M524 178 Q540 164 556 178" stroke="rgba(24,119,230,.7)" stroke-width="1.6"/>
                    <path d="M514 168 Q540 146 566 168" stroke="rgba(24,119,230,.4)" stroke-width="1.4"/>
                  </g>

                  <!-- 机身 -->
                  <g class="part" :class="hotClass('part-body')">
                    <rect x="170" y="245" width="300" height="130" rx="16"
                          fill="url(#cuBody)" stroke="#1877e6" stroke-width="2"/>
                    <rect x="188" y="262" width="264" height="26" rx="6" fill="rgba(24,119,230,.08)"/>
                    <text x="320" y="280" class="svg-mono svg-cyan" text-anchor="middle" font-size="12">
                      {{ dock?.deviceSn || '-' }}
                    </text>
                    <g fill="rgba(24,119,230,.25)">
                      <rect x="196" y="342" width="18" height="6" rx="2"/><rect x="220" y="342" width="18" height="6" rx="2"/>
                      <rect x="402" y="342" width="18" height="6" rx="2"/><rect x="426" y="342" width="18" height="6" rx="2"/>
                    </g>
                  </g>

                  <!-- 充电单元:能量粒子仅在充电时流动 -->
                  <g class="part" :class="hotClass('part-charge')">
                    <rect x="182" y="300" width="44" height="30" rx="5"
                          fill="#ffffff" stroke="#f59e0b" stroke-width="1.6"/>
                    <path d="M208 305 L199 317 H205 L203 326 L213 313 H206 Z" fill="#f59e0b"/>
                    <g v-if="chargingOn">
                      <circle r="2.4" fill="#f59e0b">
                        <animateMotion dur="1.6s" repeatCount="indefinite" path="M188 296 L188 334"/>
                      </circle>
                      <circle r="2" fill="#fcd34d">
                        <animateMotion dur="1.6s" begin=".8s" repeatCount="indefinite" path="M192 296 L192 334"/>
                      </circle>
                    </g>
                  </g>

                  <!-- 升降平台:随机体状态抬升 -->
                  <g class="part putter" :class="[hotClass('part-putter'), { up: putterOpen }]">
                    <rect x="288" y="296" width="64" height="12" rx="4" fill="#2563eb" stroke="#1877e6" stroke-width="1.5"/>
                    <rect x="304" y="308" width="32" height="34" fill="rgba(24,119,230,.15)" stroke="rgba(24,119,230,.5)"/>
                  </g>

                  <!-- 飞行器:在舱时随平台升降;离舱时显示为上方待命 -->
                  <g v-if="droneInDock" class="part drone-in" :class="[hotClass('part-drone'), { up: putterOpen }]">
                    <g stroke="#60a5fa" stroke-width="4" stroke-linecap="round">
                      <path d="M296 272 L282 258"/><path d="M344 272 L358 258"/>
                      <path d="M296 296 L282 310"/><path d="M344 296 L358 310"/>
                    </g>
                    <!-- 旋翼只在飞行器上电后旋转 -->
                    <circle cx="280" cy="256" r="13" stroke="#1877e6" stroke-width="2"
                            stroke-dasharray="8 5" fill="rgba(24,119,230,.08)"
                            class="rotor" :class="{ powered: dronePowerOn }"/>
                    <circle cx="360" cy="256" r="13" stroke="#1877e6" stroke-width="2"
                            stroke-dasharray="8 5" fill="rgba(24,119,230,.08)"
                            class="rotor r2" :class="{ powered: dronePowerOn }"/>
                    <circle cx="280" cy="312" r="13" stroke="#1877e6" stroke-width="2"
                            stroke-dasharray="8 5" fill="rgba(24,119,230,.08)"
                            class="rotor r3" :class="{ powered: dronePowerOn }"/>
                    <circle cx="360" cy="312" r="13" stroke="#1877e6" stroke-width="2"
                            stroke-dasharray="8 5" fill="rgba(24,119,230,.08)"
                            class="rotor r4" :class="{ powered: dronePowerOn }"/>
                    <rect x="298" y="268" width="44" height="32" rx="10" fill="#ffffff" stroke="#1877e6" stroke-width="1.6"/>
                    <circle cx="320" cy="284" r="7" fill="#0b3f8f"/>
                    <circle cx="320" cy="284" r="2.6" fill="#7dd3fc"/>
                  </g>
                  <g v-else class="part drone-away" :class="hotClass('part-drone')">
                    <path d="M320 226 V120" stroke="rgba(24,119,230,.35)" stroke-width="1.4" stroke-dasharray="5 7"/>
                    <g class="away-bob">
                      <circle cx="302" cy="98" r="9" stroke="#60a5fa" stroke-width="1.6" stroke-dasharray="5 4" fill="none"/>
                      <circle cx="338" cy="98" r="9" stroke="#60a5fa" stroke-width="1.6" stroke-dasharray="5 4" fill="none"/>
                      <rect x="305" y="88" width="30" height="20" rx="6" fill="#ffffff" stroke="#1877e6" stroke-width="1.4"/>
                      <circle cx="320" cy="98" r="4.5" fill="#0b3f8f"/>
                    </g>
                    <text x="320" y="80" class="svg-mono svg-cyan" text-anchor="middle" font-size="11">已离舱</text>
                  </g>

                  <!-- 舱盖:蝶翼对开 -->
                  <g class="part cover-l" :class="[hotClass('part-cover'), { open: coverOpen }]">
                    <rect x="172" y="234" width="146" height="12" rx="5" fill="url(#cuCover)" stroke="#1877e6" stroke-width="1.4"/>
                    <line x1="196" y1="240" x2="294" y2="240" stroke="rgba(255,255,255,.45)"/>
                  </g>
                  <g class="part cover-r" :class="[hotClass('part-cover'), { open: coverOpen }]">
                    <rect x="322" y="234" width="146" height="12" rx="5" fill="url(#cuCover)" stroke="#1877e6" stroke-width="1.4"/>
                    <line x1="346" y1="240" x2="444" y2="240" stroke="rgba(255,255,255,.45)"/>
                  </g>

                  <!-- 标注引线 -->
                  <g class="svg-mono svg-dim" font-size="11">
                    <path d="M110 152 L176 152 L250 234" stroke="rgba(24,119,230,.35)" fill="none"/>
                    <text x="106" y="148" text-anchor="end">舱盖 COVER</text>
                    <path d="M74 210 L92 210" stroke="rgba(24,119,230,.35)"/>
                    <text x="70" y="214" text-anchor="end">补光灯</text>
                    <path d="M108 356 L176 318" stroke="rgba(245,158,11,.55)" fill="none"/>
                    <text x="104" y="360" text-anchor="end" fill="rgba(217,119,6,.9)">充电</text>
                    <path d="M556 160 L544 178" stroke="rgba(24,119,230,.35)"/>
                    <text x="560" y="158">天线</text>
                    <path d="M548 250 L470 268 L374 272" stroke="rgba(24,119,230,.35)" fill="none"/>
                    <text x="552" y="254">飞行器</text>
                    <path d="M498 340 L412 316 L358 302" stroke="rgba(24,119,230,.35)" fill="none"/>
                    <text x="502" y="344">升降平台</text>
                  </g>
                </svg>
              </div>

              <!-- 操作台侧板:上半读数、下半拨杆 —— 看与摸在同一处 -->
              <div class="console-panel">
                <div class="hud-title">操作台 <span class="cp-mono">CONSOLE</span></div>

                <div class="hud-row"><span>状态</span><b class="hud-cyan">{{ t?.modeLabel || '-' }}</b></div>
                <div class="hud-row hud-batt">
                  <span>电量</span>
                  <div class="hud-bar">
                    <i :style="{ width: (t?.batteryPercent ?? 0) + '%' }"
                       :class="{ low: (t?.batteryPercent ?? 100) < 30 }"></i>
                  </div>
                  <b class="hud-cyan">{{ t?.batteryPercent ?? '-' }}%</b>
                </div>
                <div class="hud-row"><span>飞行器</span>
                  <b :class="droneInDock ? 'hud-cyan' : 'hud-amber'">{{ droneInDock ? '在舱' : '舱外' }}</b></div>
                <div class="hud-row"><span>网络</span>
                  <b class="hud-cyan">{{ network?.type || '-' }}·Q{{ network?.quality ?? '-' }}</b></div>

                <div class="cp-divider">
                  <span>拨杆开关</span>
                  <i>拨动即下发</i>
                </div>

                <div class="sw-row" v-for="d in cockpitSwitches" :key="d.key"
                     :class="{ busy: switchBusy === d.key }">
                  <span class="sw-led" :class="{ on: switchState(d.key) }"></span>
                  <span class="sw-label">{{ d.label }}</span>
                  <el-switch :model-value="switchState(d.key)"
                             :loading="switchBusy === d.key"
                             :disabled="dock?.status !== 'ONLINE'"
                             @change="(v) => onSwitch(d, v)" />
                </div>

                <div class="cp-divider">
                  <span>关键动作</span>
                  <i>带保护</i>
                </div>

                <div class="act-rth" v-if="rthDef">
                  <el-popconfirm title="确认一键返航?飞行器将自动返航并降落回舱"
                                 width="250" @confirm="send(rthDef)">
                    <template #reference>
                      <button class="rth-btn" :disabled="dock?.status !== 'ONLINE'"
                              :style="{ '--rth-spin': sending === 'return_home' ? 'running' : 'paused' }">
                        <el-icon :size="24"><component :is="'Position'" /></el-icon>
                      </button>
                    </template>
                  </el-popconfirm>
                  <div class="act-rth-text">
                    <b>一键返航</b>
                    <span>RTH · 二次确认</span>
                  </div>
                </div>

                <div class="cp-divider">
                  <span>维护动作</span>
                </div>

                <div class="act-flow">
                  <el-popconfirm v-for="s in actionCmds" :key="s.method"
                                 :title="s.danger ? `确认执行「${s.label}」?该操作有副作用` : `确认下发「${s.label}」?`"
                                 width="250" @confirm="send(s)">
                    <template #reference>
                      <el-button size="small" :type="s.danger ? 'danger' : 'default'" plain
                                 :disabled="dock?.status !== 'ONLINE'" :loading="sending === s.method"
                                 @mouseenter="hoverTarget = targetOf(s.method)"
                                 @mouseleave="hoverTarget = ''">
                        {{ s.label }}
                      </el-button>
                    </template>
                  </el-popconfirm>
                </div>

                <div class="hud-foot" v-if="t?.updateTime">{{ t.updateTime }}</div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ TAB 航线任务 ============ -->
        <el-tab-pane label="航线任务" name="wayline" lazy>
          <WaylineTab :dock="dock" />
        </el-tab-pane>

        <!-- ============ TAB 远程调试 ============ -->
        <el-tab-pane label="远程调试" name="debug" lazy>
          <DebugTab :dock="dock" />
        </el-tab-pane>

        <!-- ============ TAB 固件升级 ============ -->
        <el-tab-pane label="固件升级" name="firmware" lazy>
          <FirmwareTab :dock="dock" />
        </el-tab-pane>

        <!-- ============ TAB 远程日志 ============ -->
        <el-tab-pane label="远程日志" name="logs" lazy>
          <LogTab :dock="dock" />
        </el-tab-pane>

        <!-- ============ TAB AI识别 ============ -->
        <el-tab-pane label="AI识别" name="ai" lazy>
          <AiTab :dock="dock" />
        </el-tab-pane>

        <!-- ============ TAB 直播管理 ============ -->
        <el-tab-pane label="直播管理" name="live" lazy>
          <LiveTab :dock="dock" />
        </el-tab-pane>

        <!-- ============ TAB 媒体管理 ============ -->
        <el-tab-pane label="媒体管理" name="media" lazy>
          <MediaTab :dock="dock" />
        </el-tab-pane>

        <!-- ============ TAB 健康告警 ============ -->
        <el-tab-pane label="健康告警" name="hms" lazy>
          <HmsTab :dock="dock" />
        </el-tab-pane>

        <!-- ============ TAB 3 指令记录 ============ -->
        <el-tab-pane label="指令记录" name="history">
          <div class="tab-pad">
            <div class="toolbar">
              <el-select v-model="cmdFilter.method" clearable filterable placeholder="指令" style="width: 170px">
                <el-option v-for="s in services" :key="s.method" :label="s.label" :value="s.method" />
              </el-select>
              <el-select v-model="cmdFilter.status" clearable placeholder="执行结果" style="width: 120px">
                <el-option v-for="o in dictOptions(COMMAND_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-date-picker v-model="cmdFilter.range" type="daterange" value-format="YYYY-MM-DD" unlink-panels
                              range-separator="至" start-placeholder="下发开始" end-placeholder="下发结束"
                              style="width: 250px" @change="searchCmd" />
              <el-button @click="searchCmd">查询</el-button>
              <el-button @click="resetCmd">重置</el-button>
            </div>

            <el-table :data="cmdRows" v-loading="cmdLoading" stripe height="420" @sort-change="onCmdSort">
              <el-table-column type="index" label="序号" width="56"
                               :index="(i) => (cmdPager.page - 1) * cmdPager.size + i + 1" />
              <el-table-column prop="method" label="指令" width="170" sortable="custom">
                <template #default="{ row }">
                  {{ methodLabel(row.method) }}
                  <span class="dim-code">{{ row.method }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="执行结果" width="100" sortable="custom">
                <template #default="{ row }">
                  <el-tag size="small" :type="dictTag(COMMAND_STATUS, row.status)" effect="plain">
                    {{ dictLabel(COMMAND_STATUS, row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="sentAt" label="下发时间" width="165" sortable="custom" />
              <el-table-column prop="replyAt" label="回复时间" width="165" sortable="custom">
                <template #default="{ row }">{{ row.replyAt || '-' }}</template>
              </el-table-column>
              <el-table-column prop="replyJson" label="设备回复" min-width="240" show-overflow-tooltip>
                <template #default="{ row }">{{ row.replyJson || '-' }}</template>
              </el-table-column>
            </el-table>

            <div class="pager-row">
              <el-pagination background layout="total, sizes, prev, pager, next" :total="cmdPager.total"
                             v-model:current-page="cmdPager.page" v-model:page-size="cmdPager.size"
                             :page-sizes="[10, 20, 50]" @current-change="loadCmdPage" @size-change="onCmdSize" />
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ TAB 4 设备事件 ============ -->
        <el-tab-pane label="设备事件" name="events">
          <div class="tab-pad">
            <div class="toolbar">
              <el-select v-model="evtFilter.eventType" clearable placeholder="事件类型" style="width: 130px">
                <el-option v-for="o in dictOptions(DEVICE_EVENT_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-select v-model="evtFilter.level" clearable placeholder="级别" style="width: 110px">
                <el-option label="提示" value="INFO" /><el-option label="警告" value="WARN" />
                <el-option label="错误" value="ERROR" />
              </el-select>
              <el-input v-model="evtFilter.keyword" placeholder="说明 / 方法名" clearable style="width: 200px"
                        @keyup.enter="searchEvt" />
              <el-button @click="searchEvt">查询</el-button>
              <el-button @click="resetEvt">重置</el-button>
            </div>

            <el-table :data="evtRows" v-loading="evtLoading" stripe height="420" @sort-change="onEvtSort">
              <el-table-column type="index" label="序号" width="56"
                               :index="(i) => (evtPager.page - 1) * evtPager.size + i + 1" />
              <el-table-column prop="eventType" label="类型" width="110" sortable="custom">
                <template #default="{ row }">
                  <el-tag size="small" :type="dictTag(DEVICE_EVENT_TYPE, row.eventType)" effect="light">
                    {{ dictLabel(DEVICE_EVENT_TYPE, row.eventType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="level" label="级别" width="90" sortable="custom">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.level === 'ERROR' ? 'danger' : row.level === 'WARN' ? 'warning' : 'info'"
                          effect="plain">
                    {{ { INFO: '提示', WARN: '警告', ERROR: '错误' }[row.level] || row.level }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="method" label="方法" width="170" sortable="custom" show-overflow-tooltip>
                <template #default="{ row }">{{ row.method || '-' }}</template>
              </el-table-column>
              <el-table-column prop="message" label="说明" min-width="240" show-overflow-tooltip>
                <template #default="{ row }">{{ row.message || '-' }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="时间" width="170" sortable="custom" />
            </el-table>

            <div class="pager-row">
              <el-pagination background layout="total, sizes, prev, pager, next" :total="evtPager.total"
                             v-model:current-page="evtPager.page" v-model:page-size="evtPager.size"
                             :page-sizes="[10, 20, 50]" @current-change="loadEvtPage" @size-change="onEvtSize" />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import http from '../../api'
import {
  DEVICE_STATUS, COMMAND_STATUS, DEVICE_EVENT_TYPE, COVER_STATE,
  dictLabel, dictTag, dictOptions, modeTag
} from '../../utils/dict'
import WaylineTab from './dock/WaylineTab.vue'
import DebugTab from './dock/DebugTab.vue'
import FirmwareTab from './dock/FirmwareTab.vue'
import LogTab from './dock/LogTab.vue'
import AiTab from './dock/AiTab.vue'
import LiveTab from './dock/LiveTab.vue'
import MediaTab from './dock/MediaTab.vue'
import HmsTab from './dock/HmsTab.vue'

const route = useRoute()
const id = route.params.id

const loading = ref(false)
const activeTab = ref('info')
const dock = ref(null)
const t = ref(null)
const services = ref([])
const sending = ref('')
const polling = ref(true)
let timer = null

/* ---------- 指令记录 ---------- */
const cmdLoading = ref(false)
const cmdRows = ref([])
const cmdFilter = reactive({ method: '', status: '', range: null })
const cmdPager = reactive({ page: 1, size: 10, total: 0 })
const cmdSort = reactive({ sortBy: '', direction: '' })

/* ---------- 设备事件 ---------- */
const evtLoading = ref(false)
const evtRows = ref([])
const evtFilter = reactive({ eventType: '', level: '', keyword: '' })
const evtPager = reactive({ page: 1, size: 10, total: 0 })
const evtSort = reactive({ sortBy: '', direction: '' })

const coverOpen = computed(() => cmdOverride.cover ?? (t.value?.coverState ?? 0) !== 0)
const putterOpen = computed(() => cmdOverride.putter ?? (t.value?.putterState ?? 0) !== 0)
const droneInDock = computed(() => cmdOverride.droneInDock ?? (t.value?.droneInDock ?? 0) === 1)
const coverText = computed(() => dictLabel(COVER_STATE, coverOpen.value ? 1 : 0))
const putterText = computed(() => dictLabel(COVER_STATE, putterOpen.value ? 1 : 0))
const charging = computed(() => t.value?.chargingState || {})
const chargingOn = computed(() => cmdOverride.charge ?? charging.value?.state === 1)
const network = computed(() => t.value?.networkState || {})
const subDevice = computed(() => t.value?.subDevice || {})

/**
 * 指令生效后的本地状态覆盖。
 * 遥测 3 秒一刷,若只等遥测,点完「开盖」要隔几秒才看到动画;
 * 这里在设备回复 OK 时立即改写本地状态驱动动效,待遥测与之一致后自动清除覆盖、交还遥测驱动,
 * 两者不会长期打架。
 */
const cmdOverride = reactive({ cover: null, putter: null, charge: null, light: null,
  debug: null, dronePower: null, droneInDock: null })

/** 调试模式:遥测以 modeCode 1/2 近似 */
const debugOn = computed(() => cmdOverride.debug
  ?? ((t.value?.modeCode ?? 0) === 1 || (t.value?.modeCode ?? 0) === 2))
/** 飞行器电源:舱内子设备开机状态 */
const dronePowerOn = computed(() => cmdOverride.dronePower
  ?? (t.value?.subDevice?.device_online_status ?? 0) === 1)

function applyCmdEffect(method) {
  switch (method) {
    case 'cover_open': cmdOverride.cover = true; break
    case 'cover_close':
    case 'cover_force_close': cmdOverride.cover = false; break
    case 'putter_open': cmdOverride.putter = true; break
    case 'putter_close': cmdOverride.putter = false; break
    case 'charge_open': cmdOverride.charge = true; break
    case 'charge_close': cmdOverride.charge = false; break
    case 'supplement_light_open': cmdOverride.light = true; break
    case 'supplement_light_close': cmdOverride.light = false; break
    case 'debug_mode_open': cmdOverride.debug = true; break
    case 'debug_mode_close': cmdOverride.debug = false; break
    case 'drone_open': cmdOverride.dronePower = true; break
    case 'drone_close': cmdOverride.dronePower = false; break
    case 'return_home': cmdOverride.droneInDock = true; break
  }
}

/**
 * 成对的开关类指令 → 拨杆开关(驾驶舱风格)。
 * 这些指令天然是 on/off 语义,做成开关后「当前状态 + 下发方向」一眼可读,
 * 剩下的一次性动作(自检/返航/重启等)仍留在下方按钮卡片。
 */
const SWITCH_DEFS = [
  { key: 'cover', label: '舱盖', on: 'cover_open', off: 'cover_close' },
  { key: 'putter', label: '推杆', on: 'putter_open', off: 'putter_close' },
  { key: 'charge', label: '充电', on: 'charge_open', off: 'charge_close' },
  { key: 'light', label: '补光灯', on: 'supplement_light_open', off: 'supplement_light_close' },
  { key: 'debug', label: '调试模式', on: 'debug_mode_open', off: 'debug_mode_close' },
  { key: 'dronePower', label: '飞行器电源', on: 'drone_open', off: 'drone_close' }
]
const SWITCH_METHODS = new Set(SWITCH_DEFS.flatMap((d) => [d.on, d.off]))
/** 只显示该设备目录里实际存在的开关 */
const cockpitSwitches = computed(() =>
  SWITCH_DEFS.filter((d) => services.value.some((s) => s.method === d.on || s.method === d.off)))

/** 开关当前状态:指令覆盖优先,遥测兜底 */
const switchState = (key) => {
  switch (key) {
    case 'cover': return coverOpen.value
    case 'putter': return putterOpen.value
    case 'charge': return chargingOn.value
    case 'light': return lightOn.value
    case 'debug': return debugOn.value
    case 'dronePower': return dronePowerOn.value
    default: return false
  }
}
const switchBusy = ref('')

/** 远程调试 TAB 专属指令:带参数或由专门页面(航线/OTA/日志)编排,不进操作台按钮流 */
const HIDDEN_IN_CONSOLE = new Set(['flighttask_prepare','flighttask_execute','flighttask_undo','takeoff_to_point','ota_create','logs_file_list','logs_file_upload'])

/** 维护动作:开关与返航之外的一次性指令,平铺在操作台底部 */
const actionCmds = computed(() =>
  services.value.filter((s) => !SWITCH_METHODS.has(s.method) && s.method !== 'return_home' && !HIDDEN_IN_CONSOLE.has(s.method)))

/** 一键返航:操作台最醒目的动作,单独做成带保护的大红钮 */
const rthDef = computed(() => services.value.find((s) => s.method === 'return_home'))

/* ---------- 指令 → 机场部件联动 ---------- */

/** 指令方法 → SVG 部件 id:悬停/下发时高亮对应部位,让"按钮作用在哪"一眼可见 */
const CMD_TARGET = {
  cover_open: 'part-cover', cover_close: 'part-cover', cover_force_close: 'part-cover',
  putter_open: 'part-putter', putter_close: 'part-putter',
  drone_open: 'part-drone', drone_close: 'part-drone', drone_self_check: 'part-drone',
  drone_format: 'part-drone', return_home: 'part-drone', return_home_cancel: 'part-drone',
  charge_open: 'part-charge', charge_close: 'part-charge',
  supplement_light_open: 'part-light', supplement_light_close: 'part-light',
  rtk_calibration: 'part-antenna',
  debug_mode_open: 'part-body', debug_mode_close: 'part-body',
  device_reboot: 'part-body', device_format: 'part-body'
}

const hoverTarget = ref('')
const pulseTarget = ref('')

function targetOf(method) {
  return CMD_TARGET[method] || 'part-body'
}
/** 部件是否处于高亮态(悬停预览)或脉冲态(已下发) */
function hotClass(partId) {
  return {
    hot: hoverTarget.value === partId,
    pulsing: pulseTarget.value === partId
  }
}
/** 补光灯点亮状态:无专门遥测字段,调试模式近似 + 指令覆盖 */
const lightOn = computed(() => cmdOverride.light
  ?? ((t.value?.modeCode ?? 0) === 1 || (t.value?.modeCode ?? 0) === 2))

/** 指令 method → 中文名(记录列表展示用) */
function methodLabel(method) {
  const s = services.value.find((x) => x.method === method)
  return s ? s.label : method
}

onMounted(async () => {
  await loadAll()
  timer = setInterval(() => { if (polling.value) refreshRuntime() }, 3000)
})
onUnmounted(() => clearInterval(timer))

function togglePoll() { polling.value = !polling.value }

async function loadAll() {
  loading.value = true
  try {
    const [d, svcs] = await Promise.all([
      http.get(`/devices/${id}`),
      http.get(`/devices/${id}/services`)
    ])
    dock.value = d
    services.value = svcs || []
    await Promise.all([refreshRuntime(), loadCmdPage(), loadEvtPage()])
  } finally { loading.value = false }
}

/** 只刷新遥测与在线状态;两个列表 TAB 各自分页拉取,不在这里混刷 */
async function refreshRuntime() {
  const tele = await http.get(`/devices/${id}/telemetry`)
  t.value = tele
  if (dock.value) dock.value.status = tele.status
  // 遥测与指令覆盖一致时清除覆盖,动画交还遥测驱动
  if (cmdOverride.cover !== null
      && ((tele.coverState ?? 0) !== 0) === cmdOverride.cover) cmdOverride.cover = null
  if (cmdOverride.putter !== null
      && ((tele.putterState ?? 0) !== 0) === cmdOverride.putter) cmdOverride.putter = null
  const chargeState = (tele.chargingState?.state ?? 0) === 1
  if (cmdOverride.charge !== null && chargeState === cmdOverride.charge) cmdOverride.charge = null
  const inDock = (tele.droneInDock ?? 0) === 1
  if (cmdOverride.droneInDock !== null && inDock === cmdOverride.droneInDock) {
    cmdOverride.droneInDock = null
  }
  const debugState = (tele.modeCode ?? 0) === 1 || (tele.modeCode ?? 0) === 2
  if (cmdOverride.debug !== null && debugState === cmdOverride.debug) cmdOverride.debug = null
  const powered = (tele.subDevice?.device_online_status ?? 0) === 1
  if (cmdOverride.dronePower !== null && powered === cmdOverride.dronePower) {
    cmdOverride.dronePower = null
  }
}

/* ---------- 指令下发(开关与按钮共用) ---------- */
async function issue(method, label) {
  sending.value = method
  // 下发即点亮对应部件,指令在哪个部位动作一目了然
  pulseTarget.value = targetOf(method)
  setTimeout(() => { if (pulseTarget.value === targetOf(method)) pulseTarget.value = '' }, 6000)
  try {
    const cmd = await http.post(`/devices/${id}/commands`, { method })
    ElMessage.success(`「${label}」已下发`)
    // 轮询指令记录,看到结果即回联动效;开关由此驱动,失败则状态不动(开关自动弹回)
    for (let i = 0; i < 8; i++) {
      await new Promise((r) => setTimeout(r, 800))
      await loadCmdPage()
      const cur = cmdRows.value.find((c) => c.tid === cmd.tid)
      if (cur && cur.status !== 'SENT') {
        if (cur.status === 'OK') {
          ElMessage.success(`「${label}」执行成功`)
          applyCmdEffect(method)
        } else {
          ElMessage.warning(`「${label}」${dictLabel(COMMAND_STATUS, cur.status)}`)
        }
        refreshRuntime()
        return cur.status === 'OK'
      }
    }
    refreshRuntime()
    return false
  } finally { sending.value = '' }
}

async function send(svc) {
  await issue(svc.method, svc.label)
}

/** 拨杆开关:状态由遥测/覆盖驱动,拨动只负责下发,成功后开关自行到位 */
async function onSwitch(def, val) {
  switchBusy.value = def.key
  try {
    await issue(val ? def.on : def.off, `${def.label} ${val ? '开启' : '关闭'}`)
  } finally { switchBusy.value = '' }
}

/* ---------- 指令记录分页 ---------- */
function searchCmd() { cmdPager.page = 1; loadCmdPage() }
function resetCmd() {
  cmdFilter.method = ''; cmdFilter.status = ''; cmdFilter.range = null
  searchCmd()
}
function onCmdSize() { cmdPager.page = 1; loadCmdPage() }
function onCmdSort({ prop, order }) {
  cmdSort.sortBy = order ? prop : ''
  cmdSort.direction = order === 'ascending' ? 'asc' : 'desc'
  searchCmd()
}

async function loadCmdPage() {
  cmdLoading.value = true
  try {
    const res = await http.get('/devices/commands/page', {
      params: {
        deviceSn: dock.value?.deviceSn,
        page: cmdPager.page, size: cmdPager.size,
        sortBy: cmdSort.sortBy || undefined,
        direction: cmdSort.sortBy ? cmdSort.direction : undefined,
        method: cmdFilter.method || undefined,
        status: cmdFilter.status || undefined,
        startDate: cmdFilter.range?.[0] || undefined,
        endDate: cmdFilter.range?.[1] || undefined
      }
    })
    cmdRows.value = res.rows || []
    cmdPager.total = res.total || 0
  } finally { cmdLoading.value = false }
}

/* ---------- 设备事件分页 ---------- */
function searchEvt() { evtPager.page = 1; loadEvtPage() }
function resetEvt() {
  evtFilter.eventType = ''; evtFilter.level = ''; evtFilter.keyword = ''
  searchEvt()
}
function onEvtSize() { evtPager.page = 1; loadEvtPage() }
function onEvtSort({ prop, order }) {
  evtSort.sortBy = order ? prop : ''
  evtSort.direction = order === 'ascending' ? 'asc' : 'desc'
  searchEvt()
}

async function loadEvtPage() {
  evtLoading.value = true
  try {
    const res = await http.get(`/devices/${id}/events/page`, {
      params: {
        page: evtPager.page, size: evtPager.size,
        sortBy: evtSort.sortBy || undefined,
        direction: evtSort.sortBy ? evtSort.direction : undefined,
        eventType: evtFilter.eventType || undefined,
        level: evtFilter.level || undefined,
        keyword: evtFilter.keyword || undefined
      }
    })
    evtRows.value = res.rows || []
    evtPager.total = res.total || 0
  } finally { evtLoading.value = false }
}
</script>

<style scoped>
.page { padding-bottom: 20px; }
.head-left { display: flex; align-items: center; gap: 12px; }
.sn { font-family: monospace; font-size: 13px; color: var(--primary); }
.model { font-size: 12.5px; color: var(--text-dim); }
.actions { display: flex; align-items: center; gap: 10px; }
.poll-tip { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-dim); }
.poll-tip i { width: 7px; height: 7px; border-radius: 50%; background: #98a2b3; }
.poll-tip i.on { background: #12b76a; box-shadow: 0 0 7px #12b76a; }

.tabs-wrap { padding: 4px 16px 14px; height: calc(100% - 50px); display: flex; flex-direction: column; }
/* min-height:0 必须逐层下传,否则任何一层的内容都能把固定高度的父容器撑破 */
:deep(.dock-tabs) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
:deep(.el-tabs__header) { flex-shrink: 0; }
:deep(.el-tabs__content) { flex: 1; min-height: 0; }
:deep(.el-tab-pane) { height: 100%; }

.tab-pad { padding-top: 6px; }
.toolbar { display: flex; gap: 10px; padding: 4px 0 12px; flex-wrap: wrap; align-items: center; }
.pager-row { display: flex; justify-content: flex-end; padding: 12px 0 2px; }

/* ---------- TAB 基本信息 ---------- */
.info-grid { display: grid; grid-template-columns: 380px 1fr; gap: 18px; padding-top: 6px; }
@media (max-width: 1280px) { .info-grid { grid-template-columns: 1fr; } }

.viz-block { display: flex; flex-direction: column; }
.block-title {
  font-size: 13px; font-weight: 600; color: var(--text);
  margin: 0 0 10px; padding-left: 8px; border-left: 3px solid var(--primary);
}
.dock-viz { width: 100%; height: 250px; display: block; }

.cover { transform-origin: 160px 96px; transition: transform .6s cubic-bezier(.34,1.3,.64,1); }
.cover.open { transform: translateY(-30px) scale(1.06); }
.putter { transform-origin: 160px 180px; transition: transform .5s ease; }
.putter.open { transform: translateY(12px) scaleX(1.18); }
.drone { transition: opacity .4s ease, transform .5s ease; }
.drone.inside { opacity: 1; transform: translateY(0); }
.drone:not(.inside) { opacity: .18; transform: translateY(-26px); }

.viz-legend {
  display: flex; justify-content: space-around; gap: 8px; margin-top: 4px;
  font-size: 12.5px; color: var(--text-dim);
}
.viz-legend span { display: inline-flex; align-items: center; gap: 6px; }
.viz-legend i { width: 8px; height: 8px; border-radius: 50%; background: #d0d5dd; transition: all .3s; }
.viz-legend i.on { background: #12b76a; box-shadow: 0 0 8px rgba(18,183,106,.7); }

.info-main { min-width: 0; display: flex; flex-direction: column; gap: 14px; }
.metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.metric {
  display: flex; flex-direction: column; gap: 6px;
  padding: 12px 14px; border-radius: 10px;
  background: #f7f9fc; border: 1px solid var(--border);
}
.m-label { font-size: 12px; color: var(--text-dim); }
.m-value { font-size: 20px; font-weight: 700; color: var(--text); line-height: 1.2; }
.m-value i { font-size: 12px; font-weight: 500; color: var(--text-dim); font-style: normal; margin-left: 2px; }
.kv { margin-left: 6px; font-size: 12.5px; color: var(--text-dim); }
.update-time { margin: 0; font-size: 12px; color: var(--text-faint); text-align: right; }

/* ---------- TAB 指令单元:白色科技风控制台(与全站风格一致) ---------- */
.cmd-unit-wrap {
  height: 100%; overflow: hidden;
  display: flex; flex-direction: column;
  background: #f6f9fd; border-radius: 12px; padding: 12px;
  border: 1px solid var(--border);
}
.offline-alert { margin-bottom: 12px; }

/* 可视化区占满剩余高度:机场图 + 一根操作台导轨,两块即整页。
   grid-template-rows:100% 强制网格行取容器高,防止内容(导轨)把行撑高 */
.cmd-viz-row {
  flex: 1; min-height: 0; overflow: hidden;
  display: grid; grid-template-columns: 1fr 296px;
  grid-template-rows: 100%; gap: 12px;
}
@media (max-width: 1280px) {
  .cmd-viz-row { grid-template-columns: 1fr; }
  .viz-console { min-height: 320px; }
  .console-panel { max-height: 420px; }
}

.viz-console {
  position: relative; min-width: 0; min-height: 0;
  border-radius: 10px; overflow: hidden;
  border: 1px solid #dbe7f8; background: #fff;
}
/* 绝对定位脱离文档流:SVG 的固有宽高比不再撑高网格行,行高完全由剩余空间决定 */
.dock-svg { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }

.svg-mono { font-family: 'Consolas', 'Courier New', monospace; }
.svg-dim { fill: #7b9cc4; }
.svg-cyan { fill: #1877e6; }

/* 扫描线:上下往返 */
.scanline { animation: scan 5s ease-in-out infinite; }
@keyframes scan {
  0%, 100% { transform: translateY(52px); opacity: .25; }
  50% { transform: translateY(388px); opacity: .75; }
}

/* 部件联动:悬停高亮 / 下发脉冲 */
.part { transition: filter .3s ease; }
.part.hot { filter: drop-shadow(0 0 8px rgba(24, 119, 230, .85)); }
.part.pulsing { animation: partPulse 1s ease-in-out 4; }
@keyframes partPulse {
  0%, 100% { filter: drop-shadow(0 0 2px rgba(24, 119, 230, .3)); }
  50% { filter: drop-shadow(0 0 12px rgba(24, 119, 230, .95)); }
}

/* 补光灯光锥 */
.beam { opacity: .1; transition: opacity .5s; }
.beam.on { opacity: .5; }

/* 舱盖蝶翼对开:绕铰链旋转 */
.cover-l, .cover-r { transform-box: fill-box; transition: transform .7s cubic-bezier(.34, 1.3, .64, 1); }
.cover-l { transform-origin: left center; }
.cover-r { transform-origin: right center; }
.cover-l.open { transform: rotate(-118deg); }
.cover-r.open { transform: rotate(118deg); }

/* 升降平台抬升(飞行器随之) */
.putter, .drone-in { transition: transform .6s ease; }
.putter.up, .drone-in.up { transform: translateY(-42px); }

/* 旋翼:默认停转,上电(powered)后旋转 —— 驾驶舱电源开关的直观反馈 */
.rotor { animation: spin 1.1s linear infinite; animation-play-state: paused;
         transform-box: fill-box; transform-origin: center; transition: filter .3s; }
.rotor.powered { animation-play-state: running; filter: drop-shadow(0 0 4px rgba(24,119,230,.6)); }
.rotor.r2 { animation-duration: .95s; }
.rotor.r3 { animation-duration: 1.25s; }
.rotor.r4 { animation-duration: .85s; }
@keyframes spin { to { transform: rotate(360deg); } }

/* 离舱飞行器:悬停 + 轻微摆动 */
.away-bob { animation: awayBob 3.4s ease-in-out infinite; }
@keyframes awayBob {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

/* 操作台侧板:读数 + 拨杆一体,紧贴机场图;矮屏时导轨内部滚动,不撑破整页 */
.console-panel {
  display: flex; flex-direction: column; gap: 3px;
  padding: 10px 12px; border-radius: 10px;
  background: linear-gradient(180deg, #fbfdff, #f2f7fe);
  border: 1px solid #dbe7f8;
  box-shadow: inset 0 1px 0 #fff;
  overflow-y: auto; min-height: 0;
}
.console-panel::-webkit-scrollbar { width: 4px; }
.console-panel::-webkit-scrollbar-thumb { background: #c9daf3; border-radius: 2px; }
.hud-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 12px; font-weight: 700; letter-spacing: 2px; color: #1877e6;
  padding-bottom: 5px; margin-bottom: 2px;
  border-bottom: 1px dashed #c9daf3;
}
.cp-mono { font-family: 'Consolas', monospace; font-size: 10px; color: #9ab3d4; letter-spacing: 1.5px; }
.hud-row { display: flex; align-items: center; gap: 8px; font-size: 12.5px; }
.hud-row > span { color: var(--text-dim); flex-shrink: 0; min-width: 44px; }
.hud-row > b { font-family: 'Consolas', monospace; font-weight: 600; text-align: right; flex: 1; color: var(--text); }
.hud-cyan { color: #1877e6; }
.hud-amber { color: #d97706; }
.hud-green { color: #059669; }
.hud-dim { color: #98a2b3; }
.hud-batt .hud-bar { flex: 1; height: 6px; border-radius: 3px; background: #e4ecf7; overflow: hidden; }
.hud-bar i {
  display: block; height: 100%; border-radius: 3px;
  background: linear-gradient(90deg, #1877e6, #38bdf8);
  transition: width .5s ease;
}
.hud-bar i.low { background: linear-gradient(90deg, #ea580c, #f59e0b); }

/* 拨杆开关区:与读数之间用航空面板式分隔 */
.cp-divider {
  display: flex; align-items: center; justify-content: space-between;
  margin: 6px 0 2px; padding-top: 5px;
  border-top: 1px solid #dbe7f8;
  font-size: 11px; font-weight: 700; letter-spacing: 2px; color: var(--text-dim);
}
.cp-divider i { font-style: normal; font-weight: 400; letter-spacing: 0; color: var(--text-faint); font-size: 10.5px; }
.sw-row {
  display: flex; align-items: center; gap: 8px;
  padding: 2px 8px; border-radius: 7px;
  font-size: 12.5px; transition: background .2s;
}
.sw-row:hover { background: rgba(24, 119, 230, .06); }
.sw-row.busy { opacity: .7; }
.sw-led {
  width: 8px; height: 8px; border-radius: 50%;
  background: #cbd5e1; flex-shrink: 0; transition: all .3s;
}
.sw-led.on {
  background: #12b76a; box-shadow: 0 0 8px rgba(18, 183, 106, .8);
  animation: ledBreath 2s ease-in-out infinite;
}
@keyframes ledBreath { 0%, 100% { opacity: 1; } 50% { opacity: .55; } }
.sw-label { flex: 1; font-weight: 600; color: var(--text); }
.sw-row :deep(.el-switch) { --el-switch-on-color: #1877e6; }
/* 矮屏隐藏读数行(与基本信息 TAB 重复),优先保拨杆与动作全部可见、导轨免滚 */
@media (max-height: 860px) {
  .console-panel .hud-row { display: none; }
}
.hud-foot {
  margin-top: auto; padding-top: 8px;
  font-size: 11px; color: var(--text-faint); text-align: right;
  font-family: 'Consolas', monospace;
}

/* 一键返航:横排大钮,嵌在操作台导轨里 */
.act-rth {
  display: flex; align-items: center; gap: 11px;
  padding: 5px 9px; border-radius: 9px;
  background: #fff5f4; border: 1px solid #fecdc9;
}
.act-rth-text b { display: block; font-size: 13.5px; font-weight: 700; color: var(--text); }
.act-rth-text span { font-family: 'Consolas', monospace; font-size: 10px; color: #d92d20; letter-spacing: 1px; }
.rth-btn {
  width: 46px; height: 46px; border-radius: 50%; flex-shrink: 0;
  border: 3px solid #fda29b; background: radial-gradient(circle at 35% 30%, #ff6b60, #d92d20 75%);
  color: #fff; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 6px 16px -6px rgba(217, 45, 32, .7), inset 0 -3px 6px rgba(0,0,0,.25);
  transition: transform .15s, box-shadow .2s;
  position: relative;
}
.rth-btn::after {
  content: ''; position: absolute; inset: -7px; border-radius: 50%;
  border: 2px dashed rgba(217, 45, 32, .45);
  animation: rthSpin 1.2s linear infinite;
  animation-play-state: var(--rth-spin, paused);
}
@keyframes rthSpin { to { transform: rotate(360deg); } }
.rth-btn:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 10px 22px -6px rgba(217,45,32,.85); }
.rth-btn:active:not(:disabled) { transform: translateY(1px) scale(.97); }
.rth-btn:disabled { filter: grayscale(.7); cursor: not-allowed; }

/* 维护动作:小按钮流式排布 */
.act-flow { display: flex; flex-wrap: wrap; gap: 6px; }
.act-flow :deep(.el-button) { margin: 0; height: 26px; padding: 6px 11px; font-size: 12px; }

.dim-code { display: block; font-family: monospace; font-size: 11px; color: var(--text-faint); }
</style>
