package org.example.dndn.staffing.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.staffing.model.Trade;
import org.example.dndn.staffing.model.TradeNeed;
import org.example.dndn.staffing.model.ZoneMain;
import org.example.dndn.staffing.model.ZoneSub;
import org.example.dndn.staffing.repository.ZoneMainRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * TEST 전용 — {@code data/staffing-zone-demo.json} 을 읽어 구역 트리를 DB에 넣습니다.
 *
 * <p>파싱·삭제·저장·네이티브 정리까지 이 클래스 한 파일에 둡니다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/staffing/dummy")
public class DummyController {

    private static final String DEFAULT_CLASSPATH_RESOURCE = "classpath:data/staffing-zone-demo.json";

    private final ZoneMainRepository zoneMainRepository;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final JdbcTemplate jdbcTemplate;

    /**
     * @param replaceExisting true 이면 기존 인력배치 구역 관련 행을 비운 뒤 삽입
     * @param resource        비우지 않으면 기본 JSON; 예: {@code classpath:data/staffing-zone-demo.json}
     */
    @PostMapping("/seed-zones")
    @Transactional
    public ResponseEntity<BaseResponse<StaffingZoneSeedRes>> seedZones(
            @RequestParam(defaultValue = "true") boolean replaceExisting,
            @RequestParam(required = false) String resource
    ) throws IOException {
        String location = (resource == null || resource.isBlank()) ? DEFAULT_CLASSPATH_RESOURCE : resource.trim();
        Resource res = resourceLoader.getResource(location);
        if (!res.exists()) {
            throw new IllegalStateException("Resource not found: " + location);
        }

        if (replaceExisting) {
            StaffingDummyZoneWriter.purgeZoneTree(jdbcTemplate);
        }

        StaffingZoneDemoRoot root = readDemoJson(res.getInputStream());
        StaffingDummyZoneWriter.PersistCounters c = StaffingDummyZoneWriter.persist(zoneMainRepository, root);

        StaffingZoneSeedRes body = StaffingZoneSeedRes.builder()
                .resource(location)
                .replacedExisting(replaceExisting)
                .jsonVersion(root.version)
                .jsonDescription(root.description)
                .zoneMainCount(c.zoneMainCount())
                .zoneSubCount(c.zoneSubCount())
                .tradeNeedCount(c.tradeNeedCount())
                .build();
        return ResponseEntity.ok(BaseResponse.success(body));
    }

    private StaffingZoneDemoRoot readDemoJson(InputStream in) throws IOException {
        return objectMapper.readValue(in, StaffingZoneDemoRoot.class);
    }

    // ——— JSON 매핑 (Jackson) ————————————————————————————————————

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class StaffingZoneDemoRoot {
        public int version;
        public String description;
        public List<ZoneMainJson> zoneMains = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ZoneMainJson {
        public String title;
        public int displayOrder;
        public List<ZoneSubJson> zoneSubs = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ZoneSubJson {
        public String title;
        public int displayOrder;
        public int required;
        public List<TradeNeedJson> tradeNeeds = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TradeNeedJson {
        public String trade;
        public int need;
    }

    // ——— 응답 DTO ————————————————————————————————————————————————

    @Getter
    @Builder
    public static class StaffingZoneSeedRes {
        private final String resource;
        private final boolean replacedExisting;
        private final int jsonVersion;
        private final String jsonDescription;
        private final int zoneMainCount;
        private final int zoneSubCount;
        private final int tradeNeedCount;
    }

    // ——— “레포/서비스” 역할: 네이티브 정리 + 엔티티 저장 ——————————————

    /**
     * DB 정리 및 {@link ZoneMain} 트리 영속화.
     */
    static final class StaffingDummyZoneWriter {

        private StaffingDummyZoneWriter() {}

        /**
         * 외래키 역순으로 관련 행 삭제 후 구역 헤더 삭제.
         *
         * <p>표 이름은 엔티티 {@link jakarta.persistence.Table} 과 동일해야 합니다.</p>
         */
        static void purgeZoneTree(JdbcTemplate jdbcTemplate) {
            jdbcTemplate.update("DELETE FROM staffing_assignment");
            jdbcTemplate.update("DELETE FROM trade_need");
            jdbcTemplate.update("DELETE FROM zone_sub");
            jdbcTemplate.update("DELETE FROM zone_main");
        }

        static PersistCounters persist(ZoneMainRepository repo, StaffingZoneDemoRoot root) {
            int zm = 0;
            int zsCount = 0;
            int tnCount = 0;

            List<ZoneMain> toSave = new ArrayList<>();
            for (ZoneMainJson mj : root.zoneMains) {
                if (mj.title == null || mj.title.isBlank()) {
                    continue;
                }
                ZoneMain main = ZoneMain.builder()
                        .title(mj.title.trim())
                        .displayOrder(mj.displayOrder)
                        .build();

                for (ZoneSubJson sj : mj.zoneSubs) {
                    if (sj.title == null || sj.title.isBlank()) {
                        continue;
                    }
                    ZoneSub sub = ZoneSub.builder()
                            .zoneMain(main)
                            .title(sj.title.trim())
                            .required(Math.max(0, sj.required))
                            .displayOrder(sj.displayOrder)
                            .build();

                    for (TradeNeedJson tj : sj.tradeNeeds) {
                        if (tj == null || tj.trade == null || tj.trade.isBlank()) {
                            continue;
                        }
                        Trade trade = Trade.valueOf(tj.trade.trim().toUpperCase());
                        TradeNeed needRow = TradeNeed.builder()
                                .zoneSub(sub)
                                .trade(trade)
                                .need(Math.max(0, tj.need))
                                .build();
                        sub.getTradeNeeds().add(needRow);
                        tnCount++;
                    }

                    main.getZoneSubs().add(sub);
                    zsCount++;
                }

                toSave.add(main);
                zm++;
            }

            repo.saveAll(toSave);
            return new PersistCounters(zm, zsCount, tnCount);
        }

        record PersistCounters(int zoneMainCount, int zoneSubCount, int tradeNeedCount) {}
    }
}
