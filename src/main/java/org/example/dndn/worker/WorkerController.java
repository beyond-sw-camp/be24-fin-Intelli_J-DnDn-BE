package org.example.dndn.worker;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.worker.model.dto.WorkerDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequiredArgsConstructor
@RequestMapping("/management")
public class WorkerController {
    private final WorkerService workerService;

    @GetMapping("/sync")
    public ResponseEntity<BaseResponse<WorkerDto.SyncRes>> syncWorkforce(@RequestParam(required = false) String siteCode)
    {
        WorkerDto.SyncRes dto = workerService.syncWorkforce(siteCode);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }
}
