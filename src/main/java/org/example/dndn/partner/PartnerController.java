package org.example.dndn.partner;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.partner.model.PartnerDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/partner")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    // 협력사 등록
    @PostMapping
    public ResponseEntity<?> create(@RequestBody PartnerDto.Req dto) {
        Long newIdx = partnerService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    // 협력사 단일 조회
    @GetMapping("/{partnerId}")
    public ResponseEntity<?> read(@PathVariable("partnerId") Long partnerId) {
        PartnerDto.Res dto = partnerService.read(partnerId);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // 협력사 목록 조회
    @GetMapping
    public ResponseEntity<?> list() {
        List<PartnerDto.partnerRes> dtos = partnerService.list();
        return ResponseEntity.ok(BaseResponse.success(dtos));
    }

    // 협력사 정보 수정
    @PutMapping("/{partnerId}")
    public ResponseEntity<?> update(
            @PathVariable("partnerId") Long partnerId,
            @RequestBody PartnerDto.Req dto) {
        partnerService.update(partnerId, dto);
        return ResponseEntity.ok(BaseResponse.success("협력사 정보가 수정되었습니다."));
    }

    // 협력사 평가 등록/수정
    @PutMapping("/{partnerId}/evaluation")
    public ResponseEntity<?> evaluate(
            @PathVariable("partnerId") Long partnerId,
            @RequestBody PartnerDto.EvalReq dto) {
        partnerService.evaluate(partnerId, dto);
        return ResponseEntity.ok(BaseResponse.success("협력사 평가가 반영되었습니다."));
    }

    // 협력사 삭제
    @DeleteMapping("/{partnerId}")
    public ResponseEntity<?> delete(@PathVariable("partnerId") Long partnerId) {
        partnerService.delete(partnerId);
        return ResponseEntity.ok(BaseResponse.success("협력사가 삭제되었습니다."));
    }
}