package dgu.aecofarm.domain.contract.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dgu.aecofarm.dto.contract.ContractDetailResponseDTO;
import dgu.aecofarm.dto.contract.CreateContractRequestDTO;
import dgu.aecofarm.entity.*;
import dgu.aecofarm.exception.InvalidUserIdException;
import dgu.aecofarm.repository.ContractRepository;
import dgu.aecofarm.repository.ItemRepository;
import dgu.aecofarm.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public String createContract(CreateContractRequestDTO createContractRequestDTO, String memberId) {
        Member member = memberRepository.findById(Long.valueOf(memberId))
                .orElseThrow(() -> new InvalidUserIdException("유효한 사용자 ID가 아닙니다."));

        String itemHashJson;
        try {
            itemHashJson = objectMapper.writeValueAsString(createContractRequestDTO.getItemHash());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("아이템 해시를 JSON으로 변환하는데 실패했습니다.", e);
        }

        Item item = Item.builder()
                .itemName(createContractRequestDTO.getItemName())
                .price(createContractRequestDTO.getPrice())
                .itemImage(createContractRequestDTO.getItemImage())
                .itemContents(createContractRequestDTO.getItemContents())
                .itemPlace(createContractRequestDTO.getItemPlace())
                .itemHash(itemHashJson)
                .time(createContractRequestDTO.getTime())
                .contractTime(createContractRequestDTO.getContractTime())
                .kakao(createContractRequestDTO.getKakao())
                .click(0) // 기본값 설정
                .createdAt(LocalDateTime.now())
                .build();

        itemRepository.save(item);

        Contract contract = Contract.builder()
                .lendMember(createContractRequestDTO.getCategory().equals("BORROW") ? member : null)
                .borrowMember(createContractRequestDTO.getCategory().equals("LEND") ? member : null)
                .item(item)
                .category(Category.valueOf(createContractRequestDTO.getCategory()))
                .status(Status.NONE)
                .askTime(LocalDateTime.now())
                .build();

        contractRepository.save(contract);
        return "게시글 등록에 성공하였습니다.";
    }

    @Transactional
    public String updateContract(Long contractId, CreateContractRequestDTO createContractRequestDTO, String memberId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("유효한 계약 ID가 아닙니다."));

        Member member = memberRepository.findById(Long.valueOf(memberId))
                .orElseThrow(() -> new InvalidUserIdException("유효한 사용자 ID가 아닙니다."));

        // 수정 권한 체크
        boolean hasUpdatePermission = false;
        if (contract.getCategory() == Category.BORROW && contract.getLendMember() != null && contract.getLendMember().equals(member)) {
            hasUpdatePermission = true;
        } else if (contract.getCategory() == Category.LEND && contract.getBorrowMember() != null && contract.getBorrowMember().equals(member)) {
            hasUpdatePermission = true;
        }

        if (!hasUpdatePermission) {
            throw new IllegalArgumentException("수정 권한이 없습니다.");
        }

        String itemHashJson;
        try {
            itemHashJson = objectMapper.writeValueAsString(createContractRequestDTO.getItemHash());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("아이템 해시를 JSON으로 변환하는데 실패했습니다.", e);
        }

        Item item = contract.getItem();
        item.updateItemName(createContractRequestDTO.getItemName());
        item.updatePrice(createContractRequestDTO.getPrice());
        item.updateItemImage(createContractRequestDTO.getItemImage());
        item.updateItemContents(createContractRequestDTO.getItemContents());
        item.updateItemPlace(createContractRequestDTO.getItemPlace());
        item.updateItemHash(itemHashJson);
        item.updateTime(createContractRequestDTO.getTime());
        item.updateContractTime(createContractRequestDTO.getContractTime());
        item.updateKakao(createContractRequestDTO.getKakao());
        itemRepository.save(item);

        contract.updateItem(item);
        contract.updateCategory(Category.valueOf(createContractRequestDTO.getCategory()));
        contractRepository.save(contract);

        return "게시글 수정에 성공하였습니다.";
    }

    @Transactional
    public String deleteContract(Long contractId, String memberId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("유효한 계약 ID가 아닙니다."));

        Member member = memberRepository.findById(Long.valueOf(memberId))
                .orElseThrow(() -> new InvalidUserIdException("유효한 사용자 ID가 아닙니다."));

        // 삭제 권한 체크
        boolean hasDeletePermission = false;
        if (contract.getCategory() == Category.BORROW && contract.getLendMember() != null && contract.getLendMember().equals(member)) {
            hasDeletePermission = true;
        } else if (contract.getCategory() == Category.LEND && contract.getBorrowMember() != null && contract.getBorrowMember().equals(member)) {
            hasDeletePermission = true;
        }

        if (!hasDeletePermission) {
            throw new IllegalArgumentException("삭제 권한이 없습니다.");
        }

        // 아이템 삭제
        Item item = contract.getItem();
        contractRepository.delete(contract); // 먼저 계약 삭제
        itemRepository.delete(item); // 그 다음 아이템 삭제
        return "게시글 삭제에 성공하였습니다.";
    }

    @Transactional
    public ContractDetailResponseDTO getContractDetail(Long contractId, String memberId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("삭제된 게시글 입니다."));

        Item item = contract.getItem();

        List<String> itemHashList;
        try {
            itemHashList = objectMapper.readValue(item.getItemHash(), List.class);
        } catch (IOException e) {
            throw new RuntimeException("아이템 해시를 리스트로 변환하는데 실패했습니다.", e);
        }

        // 조회할 때마다 클릭 횟수를 증가시킴
        item.updateClickCount();
        itemRepository.save(item);

        // 최근 본 물품에 추가
        Member member = memberRepository.findById(Long.valueOf(memberId))
                .orElseThrow(() -> new InvalidUserIdException("유효한 사용자 ID가 아닙니다."));

        try {
            // Integer -> Long 으로 변환
            List<Integer> rawRecentList = member.getRecent() == null ? new ArrayList<>() : objectMapper.readValue(member.getRecent(), List.class);
            List<Long> recentList = new ArrayList<>();
            for (Object id : rawRecentList) {
                if (id instanceof Integer) {
                    recentList.add(((Integer) id).longValue());
                } else if (id instanceof Long) {
                    recentList.add((Long) id);
                }
            }

            // 중복된 물품이 있는 경우 제거
            int index = recentList.indexOf(contractId);
            if (index != -1) {
                recentList.remove(index);
                System.out.println(index);
            }

            // 맨 마지막에 추가
            recentList.add(contractId);
            member.updateRecent(objectMapper.writeValueAsString(recentList));
            memberRepository.save(member);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("최근 본 물품 업데이트 중 오류 발생", e);
        }

        return ContractDetailResponseDTO.builder()
                .itemName(item.getItemName())
                .price(item.getPrice())
                .itemImage(item.getItemImage())
                .itemContents(item.getItemContents())
                .itemPlace(item.getItemPlace())
                .itemHash(itemHashList)
                .time(item.getTime())
                .contractTime(item.getContractTime())
                .kakao(item.getKakao())
                .build();
    }
}