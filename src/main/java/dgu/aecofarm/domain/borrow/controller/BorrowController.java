package dgu.aecofarm.domain.borrow.controller;

import dgu.aecofarm.domain.borrow.service.BorrowService;
import dgu.aecofarm.dto.contract.CreateContractRequestDTO;
import dgu.aecofarm.entity.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/borrow")
public class BorrowController {

    private final BorrowService borrowService;

    @PostMapping("/reqeust/{contractId}")
    public Response<?> requestBorrow(@PathVariable("contractId") Long contractId, Authentication auth) {
        try {
            return Response.success(borrowService.requestBorrow(contractId, auth.getName()));
        } catch (Exception e) {
            return Response.failure(e);
        }
    }


}