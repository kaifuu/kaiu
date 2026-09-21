package com.emergency.inspection.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.entity.AiAlgorithm;
import com.emergency.inspection.entity.AiAlgorithmAlarm;
import com.emergency.inspection.entity.AiOdorStation;
import com.emergency.inspection.entity.EmergencyEvent;
import com.emergency.inspection.entity.Firmware;
import com.emergency.inspection.entity.GeoFence;
import com.emergency.inspection.entity.SafeAlert;
import com.emergency.inspection.entity.InspectDemand;
import com.emergency.inspection.entity.InspectIssue;
import com.emergency.inspection.entity.Pilot;
import com.emergency.inspection.entity.Wayline;
import com.emergency.inspection.entity.WaylineJob;
import com.emergency.inspection.entity.VideoChannel;
import com.emergency.inspection.entity.WorkOrder;
import com.emergency.inspection.entity.Hazard;
import com.emergency.inspection.entity.InspectPlan;
import com.emergency.inspection.entity.InspectPoint;
import com.emergency.inspection.entity.InspectTask;
import com.emergency.inspection.entity.SysMenu;
import com.emergency.inspection.entity.SysOrg;
import com.emergency.inspection.entity.SysRole;
import com.emergency.inspection.entity.SysTenant;
import com.emergency.inspection.entity.SysUser;
import com.emergency.inspection.mapper.EmergencyEventMapper;
import com.emergency.inspection.mapper.FirmwareMapper;
import com.emergency.inspection.mapper.InspectDemandMapper;
import com.emergency.inspection.mapper.InspectIssueMapper;
import com.emergency.inspection.mapper.PilotMapper;
import com.emergency.inspection.mapper.VideoChannelMapper;
import com.emergency.inspection.mapper.WorkOrderMapper;
import com.emergency.inspection.mapper.HazardMapper;
import com.emergency.inspection.mapper.InspectPlanMapper;
import com.emergency.inspection.mapper.InspectPointMapper;
import com.emergency.inspection.mapper.InspectTaskMapper;
import com.emergency.inspection.mapper.SysMenuMapper;
import com.emergency.inspection.mapper.SysOrgMapper;
import com.emergency.inspection.mapper.SysRoleMapper;
import com.emergency.inspection.mapper.SysTenantMapper;
import com.emergency.inspection.mapper.SysUserMapper;
import com.emergency.inspection.mapper.AiAlgorithmAlarmMapper;
import com.emergency.inspection.mapper.AiAlgorithmMapper;
import com.emergency.inspection.mapper.AiOdorStationMapper;
import com.emergency.inspection.mapper.GeoFenceMapper;
import com.emergency.inspection.mapper.SafeAlertMapper;
import com.emergency.inspection.mapper.WaylineMapper;
import com.emergency.inspection.service.WaylineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 首次启动写入演示数据:租户/组织/菜单/角色/用户 + 应急巡检业务样例。
 * 幂等:已存在用户则整段跳过,仅补齐缺失菜单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final SysTenantMapper tenantMapper;
    private final SysOrgMapper orgMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final InspectPointMapper pointMapper;
    private final InspectPlanMapper planMapper;
    private final InspectTaskMapper taskMapper;
    private final HazardMapper hazardMapper;
    private final EmergencyEventMapper eventMapper;
    private final InspectIssueMapper issueMapper;
    private final WorkOrderMapper workOrderMapper;
    private final InspectDemandMapper demandMapper;
    private final PilotMapper pilotMapper;
    private final VideoChannelMapper videoChannelMapper;
    private final WaylineMapper waylineMapper;
    private final com.emergency.inspection.mapper.WaylineJobMapper waylineJobMapper;
    private final FirmwareMapper firmwareMapper;
    private final AiAlgorithmMapper algorithmMapper;
    private final AiAlgorithmAlarmMapper algoAlarmMapper;
    private final AiOdorStationMapper odorStationMapper;
    private final GeoFenceMapper fenceMapper;
    private final SafeAlertMapper safeAlertMapper;

    /** 目标菜单布局(BIZ 业务菜单 / SYS 系统管理),path 为唯一键 */
    private static final List<SysMenu> MENU_SEED = List.of(
            new SysMenu("工作台", "/dashboard", "Monitor", SysMenu.Group.BIZ, 1),
            new SysMenu("巡检点位", "/points", "Location", SysMenu.Group.BIZ, 2),
            new SysMenu("巡检计划", "/plans", "Calendar", SysMenu.Group.BIZ, 3),
            new SysMenu("巡检任务", "/tasks", "List", SysMenu.Group.BIZ, 4),
            new SysMenu("隐患上报", "/hazards", "Warning", SysMenu.Group.BIZ, 5),
            new SysMenu("应急事件", "/events", "Bell", SysMenu.Group.BIZ, 6),
            new SysMenu("电子围栏", "/fences", "Grid", SysMenu.Group.BIZ, 7),
            new SysMenu("安全预警", "/safe-alerts", "AlarmClock", SysMenu.Group.BIZ, 7),
            new SysMenu("机场管理", "/docks", "OfficeBuilding", SysMenu.Group.BIZ, 7),
            new SysMenu("无人机管理", "/drones", "Promotion", SysMenu.Group.BIZ, 8),
            new SysMenu("服务大屏", "/screen", "DataBoard", SysMenu.Group.BIZ, 9),
            new SysMenu("问题清单", "/issues", "Warning", SysMenu.Group.SVC, 1),
            new SysMenu("工单管理", "/work-orders", "Tickets", SysMenu.Group.SVC, 2),
            new SysMenu("需求管理", "/demands", "DocumentChecked", SysMenu.Group.SVC, 3),
            new SysMenu("视频管理", "/videos", "VideoCamera", SysMenu.Group.SVC, 4),
            new SysMenu("飞手管理", "/pilots", "Avatar", SysMenu.Group.SVC, 5),
            new SysMenu("算法管理", "/algorithms", "Cpu", SysMenu.Group.SVC, 6),
            new SysMenu("人员管理", "/sys/users", "UserFilled", SysMenu.Group.SYS, 1),
            new SysMenu("角色管理", "/sys/roles", "Key", SysMenu.Group.SYS, 2),
            new SysMenu("菜单管理", "/sys/menus", "Menu", SysMenu.Group.SYS, 3),
            new SysMenu("组织管理", "/sys/orgs", "OfficeBuilding", SysMenu.Group.SYS, 4),
            new SysMenu("租户管理", "/sys/tenants", "Files", SysMenu.Group.SYS, 5),
            new SysMenu("日志管理", "/sys/logs", "Document", SysMenu.Group.SYS, 6));

    @Override
    @Transactional
    public void run(String... args) {
        ensureMenus();
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()) > 0) {
            log.info("已存在用户数据,跳过演示数据初始化");
            // 巡检服务模块是后加的,存量库也补一次(内部自带幂等判断)
            seedServiceData();
            seedDockExtData();
            seedAlgorithmData();
            seedFenceData();
            seedFlightData();
            return;
        }
        log.info("首次启动,写入演示数据 ...");

        SysTenant tenant = seedTenant();
        SysOrg root = seedOrgs();
        SysRole adminRole = seedRoles();
        seedUsers(tenant, root, adminRole);
        seedBusinessData();
        seedServiceData();
        seedDockExtData();
        seedAlgorithmData();
        seedFenceData();
        seedFlightData();

        log.info("演示数据写入完成:租户 1 / 组织 4 / 菜单 {} / 角色 3 / 用户 3", MENU_SEED.size());
    }

    /** 已废弃的菜单路径:存量库升级时移除,避免侧边栏残留旧入口 */
    private static final List<String> MENU_REMOVED = List.of("/devices");

    /** 幂等补齐缺失菜单:存量库升级时也能拿到新增菜单 */
    private void ensureMenus() {
        List<SysMenu> current = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery());
        List<String> existing = current.stream().map(SysMenu::getPath).toList();

        for (SysMenu menu : MENU_SEED) {
            if (!existing.contains(menu.getPath())) {
                menuMapper.insert(menu);
                log.info("补齐菜单: {} {}", menu.getPath(), menu.getName());
            }
        }
        for (SysMenu menu : current) {
            if (MENU_REMOVED.contains(menu.getPath())) {
                menuMapper.deleteById(menu.getId());
                log.info("移除废弃菜单: {}", menu.getPath());
            }
        }
        refreshAdminMenus();
    }

    /** ADMIN 角色始终拥有全部菜单(新增菜单后自动纳入) */
    private void refreshAdminMenus() {
        SysRole admin = roleMapper.selectOne(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getCode, SysRole.CODE_ADMIN).last("limit 1"));
        if (admin != null) {
            admin.setMenuIdsJson(allMenuIdsJson());
            roleMapper.updateById(admin);
        }
    }

    private String allMenuIdsJson() {
        return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery())
                .stream().map(SysMenu::getId).sorted().toList().toString();
    }

    private SysTenant seedTenant() {
        SysTenant tenant = new SysTenant();
        tenant.setName("默认租户");
        tenant.setCode("DEFAULT");
        tenant.setRemark("演示用内置租户");
        tenant.setEnabled(true);
        tenantMapper.insert(tenant);
        return tenant;
    }

    private SysOrg seedOrgs() {
        SysOrg root = new SysOrg();
        root.setName("市生态环境局");
        root.setOrgCode("EM");
        root.setSort(1);
        root.setEnabled(true);
        orgMapper.insert(root);

        List<SysOrg> children = new ArrayList<>();
        children.add(childOrg("环境监测中心", "EM-01", root.getId(), 1));
        children.add(childOrg("环境执法支队", "EM-02", root.getId(), 2));
        children.add(childOrg("应急指挥中心", "EM-03", root.getId(), 3));
        children.forEach(orgMapper::insert);
        return root;
    }

    private SysOrg childOrg(String name, String code, Long parentId, int sort) {
        SysOrg org = new SysOrg();
        org.setName(name);
        org.setOrgCode(code);
        org.setParentId(parentId);
        org.setSort(sort);
        org.setEnabled(true);
        return org;
    }

    private SysRole seedRoles() {
        List<SysMenu> menus = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery());

        SysRole admin = new SysRole();
        admin.setName("系统管理员");
        admin.setCode(SysRole.CODE_ADMIN);
        admin.setRemark("拥有全部菜单权限");
        admin.setMenuIdsJson(menus.stream().map(SysMenu::getId).sorted().toList().toString());
        admin.setEnabled(true);
        roleMapper.insert(admin);

        SysRole operator = new SysRole();
        operator.setName("业务操作员");
        operator.setCode("OPERATOR");
        operator.setRemark("仅业务菜单,无系统管理权限");
        operator.setMenuIdsJson(menus.stream()
                .filter(m -> m.getMenuGroup() == SysMenu.Group.BIZ)
                .map(SysMenu::getId).sorted().toList().toString());
        operator.setEnabled(true);
        roleMapper.insert(operator);

        SysRole inspector = new SysRole();
        inspector.setName("巡检员");
        inspector.setCode("INSPECTOR");
        inspector.setRemark("工作台 / 巡检任务 / 隐患上报");
        List<String> allowed = List.of("/dashboard", "/tasks", "/hazards");
        inspector.setMenuIdsJson(menus.stream()
                .filter(m -> allowed.contains(m.getPath()))
                .map(SysMenu::getId).sorted().toList().toString());
        inspector.setEnabled(true);
        roleMapper.insert(inspector);

        return admin;
    }

    private void seedUsers(SysTenant tenant, SysOrg root, SysRole adminRole) {
        SysRole operatorRole = roleMapper.selectOne(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getCode, "OPERATOR").last("limit 1"));
        SysOrg monitorCenter = orgMapper.selectOne(
                Wrappers.<SysOrg>lambdaQuery().eq(SysOrg::getOrgCode, "EM-01").last("limit 1"));

        userMapper.insert(user("admin", "admin123", "系统管理员", "13800000001",
                adminRole.getId(), root.getId(), tenant.getId()));
        userMapper.insert(user("operator", "operator123", "业务操作员", "13800000002",
                operatorRole == null ? null : operatorRole.getId(),
                monitorCenter == null ? root.getId() : monitorCenter.getId(), tenant.getId()));
        userMapper.insert(user("inspector", "inspector123", "张巡检", "13800000003",
                roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getCode, "INSPECTOR").last("limit 1")).getId(),
                monitorCenter == null ? root.getId() : monitorCenter.getId(), tenant.getId()));
    }

    private SysUser user(String username, String rawPassword, String nickname, String phone,
                         Long roleId, Long orgId, Long tenantId) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(ENCODER.encode(rawPassword));
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setRoleId(roleId);
        user.setOrgId(orgId);
        user.setTenantId(tenantId);
        user.setStatus(SysUser.Status.ENABLED);
        return user;
    }

    // ==================== 业务演示数据 ====================

    // ==================== 机场扩展能力演示数据(航线库 / 固件库) ====================

    private void seedDockExtData() {
        if (waylineMapper.selectCount(Wrappers.<Wayline>lambdaQuery()) == 0) {
            waylineMapper.insert(wayline("WL0001", "潮白河巡检航线", Wayline.TemplateTypes.INSPECT,
                    90, 8, new double[][]{
                            {116.72700, 39.91780, 90, 8},
                            {116.73210, 39.92150, 90, 8},
                            {116.73820, 39.92510, 85, 7},
                            {116.74400, 39.92980, 85, 7}},
                    "潮白河入河排污口至下游断面段,覆盖 4 个巡检点,全程沿右岸飞行"));
            waylineMapper.insert(wayline("WL0002", "密云水库环线航线", Wayline.TemplateTypes.WAYPOINT,
                    120, 10, new double[][]{
                            {116.93500, 40.48300, 120, 10},
                            {116.94120, 40.48960, 120, 10},
                            {116.94800, 40.49400, 115, 9},
                            {116.95400, 40.48800, 115, 9},
                            {116.94700, 40.48200, 115, 9}},
                    "密云水库白河主坝环线,饮用水源地保护区巡查,注意禁飞区边界"));
            waylineMapper.insert(wayline("WL0003", "永定河巡检航线", Wayline.TemplateTypes.STRIP,
                    80, 6, new double[][]{
                            {116.21500, 39.85800, 80, 6},
                            {116.22140, 39.86230, 80, 6},
                            {116.22800, 39.86600, 75, 6}},
                    "永定河晓月湖断面航带作业,低空慢速获取岸线高清影像"));
            log.info("航线库演示数据写入完成:3 条");
        }
        if (firmwareMapper.selectCount(Wrappers.<Firmware>lambdaQuery()) == 0) {
            firmwareMapper.insert(firmware(Firmware.ProductType.DOCK, "DJI Dock 2",
                    "v4.2.1", "dock2_fw_v4.2.1.bin", 186_000_000L,
                    "3f8a1c2d4e5b60718293a4b5c6d7e8f9", "修复低温环境舱盖异响,优化充电功率曲线"));
            firmwareMapper.insert(firmware(Firmware.ProductType.DOCK, "DJI Dock 2",
                    "v4.1.8", "dock2_fw_v4.1.8.bin", 182_500_000L,
                    "9a8b7c6d5e4f30211203a4b5c6d7e8f0", "增强雨雪天气推杆可靠性"));
            firmwareMapper.insert(firmware(Firmware.ProductType.DRONE, "Matrice 3D",
                    "v6.0.5", "m3d_fw_v6.0.5.bin", 245_000_000L,
                    "1d2c3b4a5968778695a4b3c2d1e0f192", "提升夜视红外噪点抑制,新增巡检拍照模板"));
            firmwareMapper.insert(firmware(Firmware.ProductType.DRONE, "Matrice 3D",
                    "v5.9.2", "m3d_fw_v5.9.2.bin", 238_000_000L,
                    "a1b2c3d4e5f60718293a4b5c6d7e8f90", "优化失控返航逻辑"));
            log.info("固件库演示数据写入完成:4 条");
        }
    }

    private Wayline wayline(String code, String name, Wayline.TemplateTypes tpl,
                            double alt, double speed, double[][] points, String remark) {
        Wayline w = WaylineService.of(name, tpl, alt, speed, waypointsJson(points), remark);
        w.setCode(code);
        return w;
    }

    // ==================== 飞行架次演示数据(大屏飞行记录 / 航线管理) ====================

    /** 架次种子 flightId 前缀:幂等判断用,与真机 flightId 区分 */
    private static final String SEED_FLIGHT_PREFIX = "FSEED";

    /** 近 6 日 10 个架次:成功 7 / 失败 2 / 取消 1(时长分钟 / 照片数 / 距今分钟) */
    private void seedFlightData() {
        if (waylineJobMapper.selectCount(Wrappers.<WaylineJob>lambdaQuery()
                .likeRight(WaylineJob::getFlightId, SEED_FLIGHT_PREFIX)) > 0) {
            return;
        }
        List<Wayline> lines = waylineMapper.selectList(Wrappers.<Wayline>lambdaQuery()
                .orderByAsc(Wayline::getId));
        if (lines.isEmpty()) {
            return;
        }
        String today = java.time.LocalDate.now().toString().replace("-", "").substring(4);
        String[][] plan = {
                // 航线序号 / 状态 / 时长分钟 / 照片数 / 距今分钟
                {"0", "SUCCESS", "16", "18", "80"},
                {"1", "SUCCESS", "24", "26", "260"},
                {"2", "SUCCESS", "15", "12", "950"},
                {"0", "FAILED", "6", "0", "1500"},
                {"1", "SUCCESS", "31", "34", "2200"},
                {"2", "SUCCESS", "19", "15", "2900"},
                {"0", "CANCELED", "0", "0", "3500"},
                {"1", "SUCCESS", "27", "22", "4300"},
                {"2", "FAILED", "4", "0", "5100"},
                {"0", "SUCCESS", "22", "19", "6400"}};
        int seq = 1;
        for (String[] p : plan) {
            Wayline w = lines.get(Integer.parseInt(p[0]));
            WaylineJob j = new WaylineJob();
            j.setFlightId(SEED_FLIGHT_PREFIX + today + "-" + seq++);
            j.setDockSn("DOCK-SIM-0001");
            j.setDroneSn("DRONE-SIM-0001");
            j.setWaylineId(w.getId());
            j.setWaylineName(w.getName());
            j.setJobType(WaylineJob.JobType.IMMEDIATE);
            j.setJobChannel(WaylineJob.JobChannel.FLIGHTTASK);
            j.setRthAltitude(120);
            WaylineJob.Status st = WaylineJob.Status.valueOf(p[1]);
            j.setStatus(st);
            LocalDateTime begin = LocalDateTime.now().minusMinutes(Long.parseLong(p[4]));
            j.setDispatchedAt(begin.minusMinutes(3));
            j.setBeginAt(begin);
            int durationMin = Integer.parseInt(p[2]);
            if (st != WaylineJob.Status.CANCELED) {
                j.setEndAt(begin.plusMinutes(durationMin));
            }
            j.setProgress(st == WaylineJob.Status.SUCCESS ? 100 : (st == WaylineJob.Status.FAILED ? 38 : 0));
            j.setMediaCount(Integer.parseInt(p[3]));
            if (st == WaylineJob.Status.FAILED) {
                j.setErrorMsg(seq % 2 == 0 ? "飞行器通信超时,已自动返航" : "电量低于安全阈值,任务中止");
            }
            waylineJobMapper.insert(j);
        }
        log.info("飞行架次演示数据写入完成:10 条");
    }

    /** 航点数组序列化:[{longitude,latitude,height,speed}] */
    private static String waypointsJson(double[][] points) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < points.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format("{\"longitude\":%.6f,\"latitude\":%.6f,\"height\":%.0f,\"speed\":%.0f}",
                    points[i][0], points[i][1], points[i][2], points[i][3]));
        }
        return sb.append(']').toString();
    }

    private Firmware firmware(Firmware.ProductType type, String model, String version,
                              String fileName, long size, String md5, String remark) {
        Firmware f = new Firmware();
        f.setProductType(type);
        f.setDeviceModel(model);
        f.setVersion(version);
        f.setFileName(fileName);
        f.setFileSize(size);
        f.setFileMd5(md5);
        f.setFileUrl("http://oss.local/firmware/" + fileName);
        f.setRemark(remark);
        return f;
    }

    // ==================== 电子围栏 + 安全预警演示数据 ====================

    /** 围栏围绕模拟器机场基地布设:作业区覆盖基地,禁飞圆在东北方向,飞行漂移约 1 分钟后触发预测/闯入预警 */
    private void seedFenceData() {
        if (fenceMapper.selectCount(Wrappers.<GeoFence>lambdaQuery()) == 0) {
            GeoFence work = new GeoFence();
            work.setName("机场基地作业区");
            work.setFenceType(GeoFence.FenceType.WORK);
            work.setShape(GeoFence.Shape.POLYGON);
            work.setPointsJson("[{\"lng\":116.394500,\"lat\":39.907200},{\"lng\":116.400500,\"lat\":39.907200},"
                    + "{\"lng\":116.400500,\"lat\":39.911200},{\"lng\":116.394500,\"lat\":39.911200}]");
            work.setEnabled(true);
            work.setRemark("巡检机场起降与保障作业区,涵盖机库、充电与备件区");
            fenceMapper.insert(work);

            GeoFence noFly = new GeoFence();
            noFly.setName("东北高压走廊禁飞区");
            noFly.setFenceType(GeoFence.FenceType.NO_FLY);
            noFly.setShape(GeoFence.Shape.CIRCLE);
            noFly.setPointsJson("[{\"lng\":116.401000,\"lat\":39.911000}]");
            noFly.setRadius(new BigDecimal("200"));
            noFly.setEnabled(true);
            noFly.setRemark("高压输电走廊上空,无人机禁止进入");
            fenceMapper.insert(noFly);

            GeoFence limit = new GeoFence();
            limit.setName("城区限飞区");
            limit.setFenceType(GeoFence.FenceType.LIMIT);
            limit.setShape(GeoFence.Shape.CIRCLE);
            limit.setPointsJson("[{\"lng\":116.393000,\"lat\":39.907000}]");
            limit.setRadius(new BigDecimal("800"));
            limit.setMaxAltitude(new BigDecimal("120"));
            limit.setEnabled(true);
            limit.setRemark("城区空域,飞行高度不得超过 120 米");
            fenceMapper.insert(limit);
            log.info("电子围栏演示数据写入完成:3 条");
        }
        if (safeAlertMapper.selectCount(Wrappers.<SafeAlert>lambdaQuery()) == 0) {
            LocalDateTime now = LocalDateTime.now();
            safeAlertMapper.insert(safeAlert(SafeAlert.AlertType.BATTERY_ANOMALY, SafeAlert.Level.WARN,
                    "DRONE-SIM-0001", "电量骤降预警", "5 分钟内电量下降 17%(当前 41%),超出正常巡检消耗速率",
                    "116.398100", "39.909800", 88, now.minusHours(5), true, "系统管理员",
                    "换电后复飞,确认为低温电池性能衰减", now.minusHours(4)));
            safeAlertMapper.insert(safeAlert(SafeAlert.AlertType.ALTITUDE_JUMP, SafeAlert.Level.WARN,
                    "DRONE-SIM-0001", "高度突变预警", "相邻遥测高度跳变 46 米(92 → 138),疑似气压计异常或强上升气流",
                    "116.397600", "39.909400", 138, now.minusHours(2), false, null, null, null));
            safeAlertMapper.insert(safeAlert(SafeAlert.AlertType.PREDICTED_BREACH, SafeAlert.Level.ERROR,
                    "DRONE-SIM-0001", "预测闯入禁飞区", "按当前航速外推 60 秒后将进入「东北高压走廊禁飞区」,建议立即调整航向",
                    "116.399200", "39.910200", 96, now.minusMinutes(40), false, null, null, null));
            log.info("安全预警历史样例写入完成:3 条");
        }
    }

    private SafeAlert safeAlert(SafeAlert.AlertType type, SafeAlert.Level level, String sn, String title,
                                String message, String lng, String lat, double height,
                                LocalDateTime occurredAt, boolean handled, String handler,
                                String handleRemark, LocalDateTime handleTime) {
        SafeAlert a = new SafeAlert();
        a.setAlertType(type);
        a.setLevel(level);
        a.setDeviceSn(sn);
        a.setTitle(title);
        a.setMessage(message);
        a.setLongitude(new BigDecimal(lng));
        a.setLatitude(new BigDecimal(lat));
        a.setHeight(BigDecimal.valueOf(height));
        a.setOccurredAt(occurredAt);
        a.setStatus(handled ? SafeAlert.Status.HANDLED : SafeAlert.Status.PENDING);
        a.setHandler(handler);
        a.setHandleRemark(handleRemark);
        a.setHandleTime(handleTime);
        return a;
    }

    // ==================== 算法管理演示数据(算法注册 / 臭气站点 / 历史告警) ====================

    private void seedAlgorithmData() {
        if (algorithmMapper.selectCount(Wrappers.<AiAlgorithm>lambdaQuery()) == 0) {
            algorithmMapper.insert(algorithm(AiAlgorithm.Code.SMOKE_FIRE, "厂区烟火识别与异常高温点定位",
                    "无人机视角可见光识别火焰/烟雾,红外镜头同步定位异常高温点;命中后自动录像取证,输出高温点坐标与温度。",
                    "厂区 / 焚烧线 / 危废暂存库", true, true, AiAlgorithm.Level.ERROR));
            algorithmMapper.insert(algorithm(AiAlgorithm.Code.ODOR_TRACE, "臭气检测与扩散溯源",
                    "汇交厂界与库区臭气传感网络读数绘制浓度分布图,结合风速风向动态模拟污染物扩散轨迹,按上风向回溯反推泄漏源。",
                    "填埋库区 / 调节池 / 罐区 / 厂界", false, false, AiAlgorithm.Level.WARN));
            algorithmMapper.insert(algorithm(AiAlgorithm.Code.ILLEGAL_DUMP, "非法倾倒识别",
                    "识别厂区内建筑垃圾/工业固废/危险废物等非法倾倒行为与区域,联动车辆轨迹与停留时长,命中即自动告警。",
                    "厂区周边 / 库区道路 / 排洪沟", false, false, AiAlgorithm.Level.WARN));
            algorithmMapper.insert(algorithm(AiAlgorithm.Code.COVER_MEMBRANE, "覆盖膜异常状态识别",
                    "填埋库区航拍影像自动识别覆盖膜破损、翘起移位、积水下陷、覆土外露等异常并量算面积。",
                    "填埋一区 / 填埋二区 / 应急调节池", false, false, AiAlgorithm.Level.WARN));
            algorithmMapper.insert(algorithm(AiAlgorithm.Code.CHIMNEY_EMISSION, "烟囱排放视觉监测",
                    "识别排气筒白烟/灰烟/黑烟/紫烟/浓黄烟羽并估算不透光度,自动录像取证并记录排放时刻。",
                    "1#/2#排气筒 / 除臭系统排气筒", false, true, AiAlgorithm.Level.WARN));
            algorithmMapper.insert(algorithm(AiAlgorithm.Code.LEAK_DETECT, "厂区泄漏检测",
                    "识别路面含盐废水、罐区/除臭系统围堰渗漏、可燃废液罐区泄漏,量算扩散面积并估算流速。",
                    "厂区道路 / 罐区围堰 / 废液罐区", false, false, AiAlgorithm.Level.ERROR));
            log.info("算法注册表写入完成:6 个算法");
        }
        if (odorStationMapper.selectCount(Wrappers.<AiOdorStation>lambdaQuery()) == 0) {
            odorStationMapper.insert(odorStation("OS-01", "填埋一区站", "填埋库区", "116.180000", "39.915000"));
            odorStationMapper.insert(odorStation("OS-02", "填埋二区站", "填埋库区", "116.178000", "39.911000"));
            odorStationMapper.insert(odorStation("OS-03", "应急调节池站", "填埋库区", "116.182000", "39.909000"));
            odorStationMapper.insert(odorStation("OS-04", "罐区站", "罐区", "116.186000", "39.916000"));
            odorStationMapper.insert(odorStation("OS-05", "除臭系统站", "除臭系统", "116.184000", "39.913000"));
            odorStationMapper.insert(odorStation("OS-06", "厂界东站", "厂界", "116.192000", "39.913000"));
            odorStationMapper.insert(odorStation("OS-07", "厂界南站", "厂界", "116.183000", "39.905000"));
            odorStationMapper.insert(odorStation("OS-08", "办公区站", "办公生活区", "116.188000", "39.919000"));
            log.info("臭气监测点位写入完成:8 站");
        }
        if (algoAlarmMapper.selectCount(Wrappers.<AiAlgorithmAlarm>lambdaQuery()) == 0) {
            seedAlarmHistory();
        }
    }

    private AiAlgorithm algorithm(AiAlgorithm.Code code, String name, String description,
                                  String scene, boolean requiresIr, boolean videoRecord,
                                  AiAlgorithm.Level alarmLevel) {
        AiAlgorithm a = new AiAlgorithm();
        a.setCode(code);
        a.setName(name);
        a.setDescription(description);
        a.setScene(scene);
        a.setRequiresIr(requiresIr);
        a.setVideoRecord(videoRecord);
        a.setEnabled(true);
        a.setConfidenceValue(80);
        a.setAlarmLevel(alarmLevel);
        a.setRunCount(0);
        return a;
    }

    private AiOdorStation odorStation(String code, String name, String area, String lng, String lat) {
        AiOdorStation s = new AiOdorStation();
        s.setStationCode(code);
        s.setStationName(name);
        s.setArea(area);
        s.setLongitude(new BigDecimal(lng));
        s.setLatitude(new BigDecimal(lat));
        return s;
    }

    /** 历史告警样例:覆盖全部 6 个算法,一条已处置,其余待处置 */
    private void seedAlarmHistory() {
        LocalDateTime now = LocalDateTime.now();
        algoAlarmMapper.insert(alarm(AiAlgorithm.Code.SMOKE_FIRE, "识别到明火,红外定位异常高温点 412℃",
                AiAlgorithmAlarm.Level.ERROR, 93, "116.181200", "39.914800", "首钢园区固废处置厂·焚烧线旁",
                "{\"fireType\":\"FLAME\",\"irMaxTempC\":412,\"ambientTempC\":26,\"tempPointLng\":116.18135,"
                        + "\"tempPointLat\":39.91492,\"thumbObjectKey\":\"media/DRONE-SIM-0001/algo/history/1-thumb.jpg\"}",
                "media/DRONE-SIM-0001/algo/history/1-smoke_fire.mp4", 46, now.minusHours(26), null, null, null));
        AiAlgorithmAlarm handled = alarm(AiAlgorithm.Code.CHIMNEY_EMISSION, "2#排气筒排放浓黄烟,不透光度 82%",
                AiAlgorithmAlarm.Level.ERROR, 88, "116.185300", "39.916100", "首钢园区固废处置厂·2#排气筒",
                "{\"smokeColor\":\"浓黄烟\",\"opacityPercent\":82,\"plumeHeightM\":35,\"chimneyId\":\"2#排气筒\","
                        + "\"thumbObjectKey\":\"media/DRONE-SIM-0001/algo/history/2-thumb.jpg\"}",
                "media/DRONE-SIM-0001/algo/history/2-chimney_emission.mp4", 62, now.minusHours(22),
                "系统管理员", "已通知焚烧线降负荷,湿法脱酸塔检修完成,复测不透光度回落至 12%",
                now.minusHours(20));
        algoAlarmMapper.insert(handled);
        algoAlarmMapper.insert(alarm(AiAlgorithm.Code.ILLEGAL_DUMP, "识别到非法倾倒(建筑垃圾),面积约 42 ㎡",
                AiAlgorithmAlarm.Level.WARN, 85, "116.179800", "39.912300", "首钢园区固废处置厂·厂区西侧围墙外",
                "{\"dumpType\":\"建筑垃圾\",\"areaM2\":42,\"dwellMinutes\":23,\"vehiclePlate\":\"京A52819\","
                        + "\"thumbObjectKey\":\"media/DRONE-SIM-0001/algo/history/3-thumb.jpg\"}",
                null, null, now.minusHours(18), null, null, null));
        algoAlarmMapper.insert(alarm(AiAlgorithm.Code.COVER_MEMBRANE, "填埋库区覆盖膜破损(约 156 ㎡)",
                AiAlgorithmAlarm.Level.WARN, 82, "116.180400", "39.915200", "首钢园区固废处置厂·填埋一区",
                "{\"anomalyType\":\"破损\",\"membraneZone\":\"填埋一区\",\"areaM2\":156,"
                        + "\"thumbObjectKey\":\"media/DRONE-SIM-0001/algo/history/4-thumb.jpg\"}",
                null, null, now.minusHours(12), null, null, null));
        algoAlarmMapper.insert(alarm(AiAlgorithm.Code.LEAK_DETECT, "识别到泄漏(罐区围堰渗漏),扩散面积约 18 ㎡",
                AiAlgorithmAlarm.Level.WARN, 91, "116.186200", "39.916300", "首钢园区固废处置厂·罐区",
                "{\"leakType\":\"罐区围堰渗漏\",\"spreadAreaM2\":18,"
                        + "\"thumbObjectKey\":\"media/DRONE-SIM-0001/algo/history/5-thumb.jpg\"}",
                null, null, now.minusHours(6), null, null, null));
        algoAlarmMapper.insert(alarm(AiAlgorithm.Code.ODOR_TRACE, "应急调节池站臭气浓度超标(H2S 0.214 ppm),溯源指向西南风向 214 m",
                AiAlgorithmAlarm.Level.ERROR, 87, "116.182000", "39.909000", "首钢园区固废处置厂·填埋库区",
                "{\"stationName\":\"应急调节池站\",\"h2sPpm\":0.214,\"nh3Ppm\":0.83,\"odorUnit\":25,"
                        + "\"sourceLng\":116.182,\"sourceLat\":39.909,\"backtrackM\":214,\"trajectoryPoints\":12,"
                        + "\"thumbObjectKey\":\"media/odor/history/6-map.jpg\"}",
                null, null, now.minusHours(3), null, null, null));
        log.info("算法告警历史样例写入完成:6 条");
    }

    private AiAlgorithmAlarm alarm(AiAlgorithm.Code code, String title, AiAlgorithmAlarm.Level level,
                                   int confidence, String lng, String lat, String address,
                                   String payloadJson, String videoKey, Integer videoSeconds,
                                   LocalDateTime occurredAt, String handler, String handleRemark,
                                   LocalDateTime handleTime) {
        AiAlgorithmAlarm a = new AiAlgorithmAlarm();
        a.setAlgorithmCode(code);
        a.setTitle(title);
        a.setLevel(level);
        a.setConfidence(confidence);
        a.setLongitude(new BigDecimal(lng));
        a.setLatitude(new BigDecimal(lat));
        a.setAddress(address);
        a.setPayloadJson(payloadJson);
        a.setVideoObjectKey(videoKey);
        a.setVideoSeconds(videoSeconds);
        a.setOccurredAt(occurredAt);
        a.setStatus(handler == null ? AiAlgorithmAlarm.Status.PENDING : AiAlgorithmAlarm.Status.HANDLED);
        a.setHandler(handler);
        a.setHandleRemark(handleRemark);
        a.setHandleTime(handleTime);
        return a;
    }

    // ==================== 巡检服务演示数据(大屏与运营管理) ====================
    private void seedServiceData() {
        // 幂等:已有问题数据说明灌过,直接跳过(存量库升级时不会重复插入)
        if (issueMapper.selectCount(Wrappers.<InspectIssue>lambdaQuery()) > 0) {
            return;
        }

        // ---------- 飞手 ----------
        List<Pilot> pilots = new ArrayList<>();
        pilots.add(pilot("张明远", "13800001001", 32, 6, "通州片区", Pilot.CertType.CAAC, "中国民用航空局"));
        pilots.add(pilot("李思远", "13800001002", 28, 4, "丰台片区", Pilot.CertType.CAAC, "中国民用航空局"));
        pilots.add(pilot("王浩然", "13800001003", 35, 8, "朝阳片区", Pilot.CertType.CAAC, "中国民用航空局"));
        pilots.add(pilot("陈志强", "13800001004", 29, 5, "密云片区", Pilot.CertType.CAAC, "中国民用航空局"));
        pilots.add(pilot("刘雅婷", "13800001005", 26, 3, "石景山片区", Pilot.CertType.UTC, "大疆慧飞培训中心"));
        pilots.add(pilot("赵鹏飞", "13800001006", 31, 6, "海淀片区", Pilot.CertType.CAAC, "中国民用航空局"));
        pilots.add(pilot("孙佳怡", "13800001007", 24, 2, "通州片区", Pilot.CertType.UTC, "大疆慧飞培训中心"));
        pilots.add(pilot("周建国", "13800001008", 41, 12, "密云片区", Pilot.CertType.AOPA, "中国航空器拥有者及驾驶员协会"));
        pilots.forEach(pilotMapper::insert);
        pilots.get(0).setStatus(Pilot.Status.ON_TASK);
        pilots.get(2).setStatus(Pilot.Status.ON_TASK);
        pilots.get(4).setStatus(Pilot.Status.LEAVE);
        pilots.forEach(pilotMapper::updateById);

        // ---------- 视频通道 ----------
        List<VideoChannel> channels = new ArrayList<>();
        channels.add(channel(1, "潮白河机场-可见光", "DOCK-EM-001", "潮白河巡检机场", VideoChannel.Protocol.FLV,
                "http://media.local/live/dock-em-001.flv", "1080P", VideoChannel.Status.ONLINE));
        channels.add(channel(2, "潮白河机场-红外", "DOCK-EM-001", "潮白河巡检机场", VideoChannel.Protocol.RTMP,
                "rtmp://media.local/live/dock-em-001-ir", "720P", VideoChannel.Status.ONLINE));
        channels.add(channel(3, "永定河机场-可见光", "DOCK-EM-002", "永定河巡检机场", VideoChannel.Protocol.FLV,
                "http://media.local/live/dock-em-002.flv", "1080P", VideoChannel.Status.ONLINE));
        channels.add(channel(4, "密云水库机场-可见光", "DOCK-EM-003", "密云水库巡检机场", VideoChannel.Protocol.GB28181,
                "gb28181://34020000001320000001", "1080P", VideoChannel.Status.OFFLINE));
        channels.add(channel(5, "西山机场-可见光", "DOCK-EM-004", "西山巡检机场", VideoChannel.Protocol.HLS,
                "http://media.local/live/dock-em-004.m3u8", "720P", VideoChannel.Status.ONLINE));
        VideoChannel playback = channel(6, "潮白河机场-历史回放", "DOCK-EM-001", "潮白河巡检机场",
                VideoChannel.Protocol.HLS, "http://media.local/vod/dock-em-001.m3u8", "1080P",
                VideoChannel.Status.ONLINE);
        playback.setChannelType(VideoChannel.ChannelType.PLAYBACK);
        channels.add(playback);
        channels.forEach(videoChannelMapper::insert);

        // ---------- 巡检问题(AI 识别) ----------
        String[][] issueSeed = {
                {"潮白河下游水面漂浮物聚集", "FLOATING", "潮白河入河排污口", "通州网格", "116.732100", "39.921500", "PENDING", "通州区水务局"},
                {"永定河晓月湖岸线疑似违建", "ILLEGAL_BUILD", "永定河晓月湖断面", "丰台网格", "116.221400", "39.862300", "WORK_ORDER", "丰台区城市管理局"},
                {"首钢园区北侧渣土车遗撒", "SLUDGE", "首钢园区固废暂存场", "石景山网格", "116.183600", "39.913200", "DISPATCHED", "石景山区城市管理局"},
                {"密云水库上游非法围网养殖", "ILLEGAL_NET", "密云水库饮用水源地", "密云网格", "116.941200", "40.489600", "PENDING", "密云区生态环境局"},
                {"西山森林公园边缘垃圾堆放", "GARBAGE", "西山国家森林公园", "海淀网格", "116.187500", "39.961700", "CLOSED", "海淀区园林绿化局"},
                {"潮白河右岸疑似排污口", "OUTFALL", "潮白河入河排污口", "通州网格", "116.734000", "39.923000", "WORK_ORDER", "通州区生态环境局"},
                {"奥体中心周边施工堆料", "CONSTRUCTION", "奥体中心空气自动站", "朝阳网格", "116.393700", "39.990800", "PENDING", "朝阳区住房和城乡建设委员会"},
                {"永定河堤顶路路面裂纹", "CRACK", "永定河晓月湖断面", "丰台网格", "116.220000", "39.864000", "DISPATCHED", "丰台区水务局"},
                {"潮白河水域水草过度繁殖", "WATER_PLANT", "潮白河入河排污口", "通州网格", "116.730000", "39.920000", "PENDING", "通州区水务局"},
                {"西山游步道旁垃圾堆放", "GARBAGE", "西山国家森林公园", "海淀网格", "116.189000", "39.960000", "WORK_ORDER", "海淀区园林绿化局"},
                {"首钢园区南侧疑似违建", "ILLEGAL_BUILD", "首钢园区固废暂存场", "石景山网格", "116.182000", "39.911000", "PENDING", "石景山区城市管理局"},
                {"密云水库岸线施工堆料", "CONSTRUCTION", "密云水库饮用水源地", "密云网格", "116.940000", "40.488000", "CLOSED", "密云区水务局"}};

        List<InspectIssue> issues = new ArrayList<>();
        for (int i = 0; i < issueSeed.length; i++) {
            String[] r = issueSeed[i];
            InspectIssue issue = new InspectIssue();
            issue.setCode(String.format("IS%06d", i + 1));
            issue.setTitle(r[0]);
            issue.setIssueType(InspectIssue.IssueType.valueOf(r[1]));
            issue.setPointName(r[2]);
            issue.setAddress("北京市" + r[2] + "周边");
            issue.setLongitude(new BigDecimal(r[4]));
            issue.setLatitude(new BigDecimal(r[5]));
            issue.setDeviceName(r[3].replace("网格", "巡检机"));
            issue.setDeviceSn("DOCK-SIM-0001");
            issue.setSource(InspectIssue.Source.AI);
            issue.setDept(r[7]);
            issue.setDescription("无人机例行巡检中由 AI 算法自动识别,已留存现场影像,待责任部门核实处置。");
            issue.setFoundAt(LocalDateTime.now().minusHours(i * 5L + 2));
            issue.setStatus(InspectIssue.Status.valueOf(r[6]));
            issues.add(issue);
        }
        issues.forEach(issueMapper::insert);

        // ---------- 工单(由问题派发) ----------
        String[][] orderSeed = {
                {"1", "PROCESSING", "HIGH", "刘伟", "通州区水务局河道管理科"},
                {"5", "CLOSED", "NORMAL", "赵敏", "丰台区城市管理综合执法大队"},
                {"2", "HANDLED", "NORMAL", "陈刚", "石景山区城市管理委员会"},
                {"3", "PROCESSING", "URGENT", "孙丽", "密云区生态环境综合执法大队"},
                {"4", "CLOSED", "LOW", "周强", "海淀区园林绿化服务中心"},
                {"9", "PENDING", "NORMAL", null, null},
                {"11", "PENDING", "NORMAL", null, null},
                {"7", "PROCESSING", "HIGH", "王海燕", "丰台区水务局工程科"},
                {"10", "HANDLED", "NORMAL", "李建国", "海淀区园林绿化服务中心"},
                {"8", "PROCESSING", "NORMAL", "赵志强", "朝阳区建设工程安全监督站"}};

        for (int i = 0; i < orderSeed.length; i++) {
            String[] r = orderSeed[i];
            InspectIssue issue = issues.get(Integer.parseInt(r[0]));
            WorkOrder order = new WorkOrder();
            order.setCode(String.format("WO%s%04d", LocalDate.now().toString().replace("-", ""), i + 1));
            order.setTitle(issue.getTitle());
            order.setIssueId(issue.getId());
            order.setIssueTitle(issue.getTitle());
            order.setIssueType(issue.getIssueType().name());
            order.setPointName(issue.getPointName());
            order.setAddress(issue.getAddress());
            order.setLongitude(issue.getLongitude());
            order.setLatitude(issue.getLatitude());
            order.setDept(issue.getDept());
            // 列序:r[1] 状态 / r[2] 优先级
            order.setStatus(WorkOrder.Status.valueOf(r[1]));
            order.setPriority(WorkOrder.Priority.valueOf(r[2]));
            order.setHandler(r[3]);
            order.setHandlerPhone(r[3] == null ? null : "1390000200" + (i + 1));
            order.setHandleDept(r[4]);
            order.setDescription(issue.getDescription());
            order.setDeadline(LocalDateTime.now().plusDays(7 - i % 5));
            if (order.getStatus() != WorkOrder.Status.PENDING) {
                order.setDispatchedAt(LocalDateTime.now().minusHours(i * 6L + 3));
            }
            if (order.getStatus() == WorkOrder.Status.HANDLED || order.getStatus() == WorkOrder.Status.CLOSED) {
                order.setResult("已现场核实并完成处置,现场复查通过。");
                order.setFinishedAt(LocalDateTime.now().minusHours(i * 4L + 1));
            }
            workOrderMapper.insert(order);
        }

        // ---------- 巡检需求 ----------
        String[][] demandSeed = {
                {"通州区水务局", "潮白河汛前水面漂浮物专项巡查", "SPECIAL", "PENDING", "7"},
                {"丰台区城市管理局", "永定河沿线违建排查", "SPECIAL", "EXECUTED", "-12"},
                {"密云区生态环境局", "密云水库饮用水源地月度巡查", "DAILY", "PENDING", "3"},
                {"石景山区城市管理局", "首钢园区渣土运输专项检查", "SPECIAL", "OVERDUE", "-4"},
                {"海淀区园林绿化局", "西山国家森林公园防火期巡查", "EMERGENCY", "EXECUTED", "-20"},
                {"朝阳区生态环境局", "奥体中心周边扬尘巡查", "DAILY", "PENDING", "5"},
                {"通州区水务局", "潮白河排污口溯源排查", "SPECIAL", "EXECUTED", "-8"},
                {"密云区水务局", "密云水库岸线施工行为巡查", "SPECIAL", "CANCELED", "-15"}};

        for (int i = 0; i < demandSeed.length; i++) {
            String[] r = demandSeed[i];
            InspectDemand demand = new InspectDemand();
            demand.setCode(String.format("DM%06d", i + 1));
            demand.setTitle(r[1]);
            demand.setSourceDept(r[0]);
            demand.setCategory(r[2]);
            demand.setStatus(InspectDemand.Status.valueOf(r[3]));
            demand.setDescription("由业务部门提报的巡检需求,待调度无人机执行。");
            demand.setExpectDate(LocalDate.now().plusDays(Long.parseLong(r[4])));
            demand.setSubmittedAt(LocalDateTime.now().minusDays(Math.abs(Long.parseLong(r[4]))));
            if (demand.getStatus() == InspectDemand.Status.EXECUTED) {
                demand.setExecutor("张明远");
                demand.setExecutedAt(LocalDateTime.now().minusDays(2));
            }
            demandMapper.insert(demand);
        }

        log.info("巡检服务演示数据写入完成:飞手 {} / 视频通道 {} / 问题 {} / 工单 {} / 需求 {}",
                pilots.size(), channels.size(), issues.size(), orderSeed.length, demandSeed.length);
    }

    private Pilot pilot(String name, String phone, int age, int years, String area,
                        Pilot.CertType cert, String org) {
        Pilot p = new Pilot();
        p.setName(name);
        p.setPhone(phone);
        p.setAge(age);
        p.setExperienceYears(years);
        p.setArea(area);
        p.setCertType(cert);
        p.setCertOrg(org);
        p.setCertNo("UAS-" + phone.substring(phone.length() - 6));
        p.setStatus(Pilot.Status.AVAILABLE);
        return p;
    }

    private VideoChannel channel(int seq, String name, String deviceSn, String deviceName,
                                 VideoChannel.Protocol protocol, String url, String resolution,
                                 VideoChannel.Status status) {
        VideoChannel c = new VideoChannel();
        // 编码必须由调用方给序号:此处若用 selectCount 生成,构建期还没插入,六条会算出同一个值
        c.setCode(String.format("VC%04d", seq));
        c.setName(name);
        c.setDeviceSn(deviceSn);
        c.setDeviceName(deviceName);
        c.setChannelType(VideoChannel.ChannelType.LIVE);
        c.setProtocol(protocol);
        c.setStreamUrl(url);
        c.setResolution(resolution);
        c.setStatus(status);
        if (status == VideoChannel.Status.ONLINE) {
            c.setLastFrameAt(LocalDateTime.now());
        }
        return c;
    }

    private void seedBusinessData() {
        // 坐标口径:北京生态环境巡检对象
        List<InspectPoint> points = List.of(
                point("潮白河入河排污口", "PT-001", InspectPoint.Category.OUTFALL, InspectPoint.RiskLevel.EXTREME,
                        "通州网格", "北京市通州区潮白河右岸", "116.732100", "39.921500", "李建国", "13900000001"),
                point("永定河晓月湖断面", "PT-002", InspectPoint.Category.RIVER, InspectPoint.RiskLevel.HIGH,
                        "丰台网格", "北京市丰台区永定河晓月湖", "116.221400", "39.862300", "王海燕", "13900000002"),
                point("奥体中心空气自动站", "PT-003", InspectPoint.Category.AIR, InspectPoint.RiskLevel.MEDIUM,
                        "朝阳网格", "北京市朝阳区奥林匹克公园", "116.393700", "39.990800", "赵志强", "13900000003"),
                point("密云水库饮用水源地", "PT-004", InspectPoint.Category.WATER_SOURCE, InspectPoint.RiskLevel.EXTREME,
                        "密云网格", "北京市密云区密云水库白河主坝", "116.941200", "40.489600", "陈晓明", "13900000004"),
                point("首钢园区固废暂存场", "PT-005", InspectPoint.Category.SOLID_WASTE, InspectPoint.RiskLevel.HIGH,
                        "石景山网格", "北京市石景山区首钢园区", "116.183600", "39.913200", "刘伟", "13900000005"),
                point("西山国家森林公园", "PT-006", InspectPoint.Category.FOREST, InspectPoint.RiskLevel.MEDIUM,
                        "海淀网格", "北京市海淀区西山国家森林公园", "116.187500", "39.961700", "孙丽", "13900000006"));
        points.forEach(pointMapper::insert);

        LocalDate today = LocalDate.now();

        InspectPlan daily = plan("重点流域日常巡检计划", "PL-2026-001", InspectPlan.Category.DAILY,
                InspectPlan.CycleType.DAY, 1, today.minusDays(7), today.plusDays(30),
                "李建国", "13900000001", points, InspectPlan.Status.ENABLED,
                "覆盖排污口、河道断面、水源地等 6 个重点对象,每日一次");
        InspectPlan special = plan("汛期水质专项排查计划", "PL-2026-002", InspectPlan.Category.SPECIAL,
                InspectPlan.CycleType.WEEK, 1, today, today.plusDays(60),
                "王海燕", "13900000002", points.subList(0, 2), InspectPlan.Status.ENABLED,
                "汛期加密排查入河排污口与河道断面,防范汛期水质波动");
        InspectPlan emergency = plan("重污染天气应急巡查", "PL-2026-003", InspectPlan.Category.EMERGENCY,
                InspectPlan.CycleType.ONCE, 1, today.plusDays(3), today.plusDays(5),
                "赵志强", "13900000003", points.subList(2, 6), InspectPlan.Status.DRAFT,
                "重污染天气期间对空气站、固废堆场、林区开展应急巡查,待审批启用");
        List.of(daily, special, emergency).forEach(planMapper::insert);

        // 任务:覆盖各状态,便于工作台与列表页展示
        InspectTask t1 = task("重点流域日常巡检计划 - 潮白河入河排污口", daily, points.get(0), "李建国",
                today.atTime(8, 0), today.atTime(10, 0), InspectTask.Status.DONE, InspectTask.Result.ABNORMAL);
        t1.setActualStart(today.atTime(8, 5));
        t1.setActualEnd(today.atTime(9, 40));
        t1.setRemark("排污口下游水体发黑伴异味,现场快检 COD 超标,已上报隐患");
        taskMapper.insert(t1);

        InspectTask t2 = task("重点流域日常巡检计划 - 永定河晓月湖断面", daily, points.get(1), "王海燕",
                today.atTime(9, 0), today.atTime(11, 0), InspectTask.Status.DONE, InspectTask.Result.NORMAL);
        t2.setActualStart(today.atTime(9, 10));
        t2.setActualEnd(today.atTime(10, 30));
        taskMapper.insert(t2);

        InspectTask t3 = task("重点流域日常巡检计划 - 奥体中心空气自动站", daily, points.get(2), "赵志强",
                today.atTime(14, 0), today.atTime(17, 0), InspectTask.Status.RUNNING, null);
        t3.setActualStart(today.atTime(14, 12));
        taskMapper.insert(t3);

        InspectTask t4 = task("汛期水质专项排查计划 - 密云水库饮用水源地", special, points.get(3), "陈晓明",
                today.atTime(15, 0), today.atTime(18, 0), InspectTask.Status.PENDING, null);
        taskMapper.insert(t4);

        InspectTask t5 = task("重点流域日常巡检计划 - 首钢园区固废暂存场", daily, points.get(4), "刘伟",
                today.minusDays(1).atTime(8, 0), today.minusDays(1).atTime(12, 0),
                InspectTask.Status.OVERDUE, null);
        taskMapper.insert(t5);

        InspectTask t6 = task("重点流域日常巡检计划 - 西山国家森林公园", daily, points.get(5), "孙丽",
                today.plusDays(1).atTime(9, 0), today.plusDays(1).atTime(11, 0),
                InspectTask.Status.PENDING, null);
        taskMapper.insert(t6);

        // 隐患
        hazardMapper.insert(hazard("入河排污口水质异常", points.get(0), t1.getId(), Hazard.Level.SEVERE,
                "排污口下游 200 米处水体发黑并伴有异味,现场快速检测 COD 约 68 mg/L,超出地表水Ⅳ类标准,需排查上游排放源。",
                "李建国", LocalDateTime.now().minusHours(4), Hazard.Status.PROCESSING, "李建国",
                "已联合执法支队溯源排查,临时封堵该排口并采样送检", LocalDateTime.now().minusHours(2)));
        hazardMapper.insert(hazard("河道断面氨氮超标", points.get(1), t2.getId(), Hazard.Level.MAJOR,
                "晓月湖断面氨氮检测值 2.4 mg/L,超过Ⅲ类水质标准,疑与上游农业面源来水有关。",
                "王海燕", LocalDateTime.now().minusHours(6), Hazard.Status.RECTIFIED, "王海燕",
                "已加密监测频次并函告上游属地,连续三日检测已回落至达标", LocalDateTime.now().minusHours(3)));
        hazardMapper.insert(hazard("固废暂存场防渗层破损", points.get(4), null, Hazard.Level.MAJOR,
                "暂存场东侧防渗膜出现约 3 平方米破损,雨天存在渗滤液下渗污染土壤与地下水的风险。",
                "赵志强", LocalDateTime.now().minusHours(1), Hazard.Status.PENDING, null, null, null));
        hazardMapper.insert(hazard("水源地保护区违规垂钓", points.get(3), t4.getId(), Hazard.Level.GENERAL,
                "白河主坝下游 500 米处发现 3 名人员违规垂钓,属饮用水水源一级保护区禁止行为。",
                "陈晓明", LocalDateTime.now().minusDays(1).withHour(16).withMinute(20).withSecond(0).withNano(0),
                Hazard.Status.CLOSED, "陈晓明",
                "已现场劝离并进行法规告知,加设警示牌,隐患消除",
                LocalDateTime.now().minusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0)));

        // 环境应急事件
        eventMapper.insert(event("潮白河水质异常预警", EmergencyEvent.Category.WATER_POLLUTION,
                EmergencyEvent.Level.III, "北京市通州区潮白河右岸",
                "116.732100", "39.921500", LocalDateTime.now().minusHours(4), "李建国", "13900000001",
                "巡检发现排污口下游水体发黑伴异味,在线监测显示 COD 与氨氮同步上升,暂未影响下游水源地取水。",
                EmergencyEvent.Status.RESPONDING, "赵志强",
                "已启动三级响应,上游闸口限流,加密监测频次至每小时一次,同步开展排放源溯源", null));
        eventMapper.insert(event("重污染天气过程", EmergencyEvent.Category.AIR_POLLUTION,
                EmergencyEvent.Level.IV, "北京市朝阳区奥林匹克公园",
                "116.393700", "39.990800",
                LocalDateTime.now().minusDays(1).withHour(7).withMinute(30).withSecond(0).withNano(0),
                "刘伟", "13900000005",
                "区域传输叠加本地积累,PM2.5 浓度持续升高,空气自动站连续 6 小时处于中度污染。",
                EmergencyEvent.Status.HANDLED, "王海燕",
                "已启动重污染天气黄色预警,督促周边工地停工、加密道路清扫,污染过程结束",
                LocalDateTime.now().minusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0)));
        eventMapper.insert(event("固废堆场渗滤液外溢", EmergencyEvent.Category.SOIL_POLLUTION,
                EmergencyEvent.Level.III, "北京市石景山区首钢园区",
                "116.183600", "39.913200",
                LocalDateTime.now().minusDays(3).withHour(13).withMinute(10).withSecond(0).withNano(0),
                "王海燕", "13900000002",
                "强降雨导致固废暂存场渗滤液收集池溢流,约 20 平方米场地受污染。",
                EmergencyEvent.Status.ARCHIVED, "李建国",
                "已完成渗滤液收集与受污染场地修复,土壤与地下水检测达标后归档",
                LocalDateTime.now().minusDays(3).withHour(14).withMinute(30).withSecond(0).withNano(0)));
    }

    private InspectPoint point(String name, String code, InspectPoint.Category category,
                               InspectPoint.RiskLevel risk, String area, String address,
                               String lng, String lat, String manager, String phone) {
        InspectPoint p = new InspectPoint();
        p.setName(name);
        p.setCode(code);
        p.setCategory(category);
        p.setRiskLevel(risk);
        p.setArea(area);
        p.setAddress(address);
        p.setLongitude(new BigDecimal(lng));
        p.setLatitude(new BigDecimal(lat));
        p.setManager(manager);
        p.setManagerPhone(phone);
        p.setStatus(InspectPoint.Status.ENABLED);
        return p;
    }

    private InspectPlan plan(String name, String code, InspectPlan.Category category, InspectPlan.CycleType cycleType,
                             int cycleValue, LocalDate start, LocalDate end, String owner, String ownerPhone,
                             List<InspectPoint> points, InspectPlan.Status status, String remark) {
        InspectPlan plan = new InspectPlan();
        plan.setName(name);
        plan.setCode(code);
        plan.setCategory(category);
        plan.setCycleType(cycleType);
        plan.setCycleValue(cycleValue);
        plan.setStartDate(start);
        plan.setEndDate(end);
        plan.setOwner(owner);
        plan.setOwnerPhone(ownerPhone);
        plan.setPointIds(points.stream().map(InspectPoint::getId).toList().toString());
        plan.setStatus(status);
        plan.setRemark(remark);
        return plan;
    }

    private InspectTask task(String name, InspectPlan plan, InspectPoint point, String executor,
                             LocalDateTime planStart, LocalDateTime planEnd,
                             InspectTask.Status status, InspectTask.Result result) {
        InspectTask task = new InspectTask();
        task.setName(name);
        task.setPlanId(plan.getId());
        task.setPointId(point.getId());
        task.setPointName(point.getName());
        task.setExecutor(executor);
        task.setExecutorPhone(point.getManagerPhone());
        task.setPlanStart(planStart);
        task.setPlanEnd(planEnd);
        task.setStatus(status);
        task.setResult(result);
        return task;
    }

    private Hazard hazard(String title, InspectPoint point, Long taskId, Hazard.Level level, String description,
                          String reporter, LocalDateTime reportTime, Hazard.Status status,
                          String handler, String handleResult, LocalDateTime handleTime) {
        Hazard hazard = new Hazard();
        hazard.setTitle(title);
        hazard.setPointId(point.getId());
        hazard.setPointName(point.getName());
        hazard.setTaskId(taskId);
        hazard.setLevel(level);
        hazard.setDescription(description);
        hazard.setReporter(reporter);
        hazard.setReportTime(reportTime);
        hazard.setStatus(status);
        hazard.setHandler(handler);
        hazard.setHandleResult(handleResult);
        hazard.setHandleTime(handleTime);
        hazard.setDeadline(reportTime.plusDays(level == Hazard.Level.SEVERE ? 1 : 3));
        return hazard;
    }

    private EmergencyEvent event(String title, EmergencyEvent.Category category, EmergencyEvent.Level level,
                                 String address, String lng, String lat, LocalDateTime occurTime,
                                 String reporter, String reporterPhone, String description,
                                 EmergencyEvent.Status status, String commander, String measure,
                                 LocalDateTime finishTime) {
        EmergencyEvent event = new EmergencyEvent();
        event.setTitle(title);
        event.setCategory(category);
        event.setLevel(level);
        event.setAddress(address);
        event.setLongitude(new BigDecimal(lng));
        event.setLatitude(new BigDecimal(lat));
        event.setOccurTime(occurTime);
        event.setReporter(reporter);
        event.setReporterPhone(reporterPhone);
        event.setDescription(description);
        event.setStatus(status);
        event.setCommander(commander);
        event.setMeasure(measure);
        event.setFinishTime(finishTime);
        return event;
    }
}
