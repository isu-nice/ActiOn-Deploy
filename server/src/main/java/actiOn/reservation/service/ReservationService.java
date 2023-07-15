package actiOn.reservation.service;

import actiOn.auth.utils.AuthUtil;
import actiOn.exception.BusinessLogicException;
import actiOn.exception.ExceptionCode;
import actiOn.item.dto.ItemDto;
import actiOn.item.entity.Item;
import actiOn.item.repository.ItemRepository;
import actiOn.member.entity.Member;
import actiOn.member.service.MemberService;
import actiOn.reservation.entity.Reservation;
import actiOn.reservation.entity.ReservationItem;
import actiOn.reservation.repository.ReservationItemRepository;
import actiOn.reservation.repository.ReservationRepository;
import actiOn.store.entity.Store;
import actiOn.store.service.StoreService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Transactional
@Service
@AllArgsConstructor
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final StoreService storeService;
    private final MemberService memberService;
    private final ItemRepository itemRepository;
    private final ReservationItemRepository reservationItemRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void postReservation(Long storeId, Reservation reservation, List<ReservationItem> reservationItems) {
        //Todo store 존재하는지 여부 확인 -> 예외처리 리팩토링 필요
        Store store = storeService.findStoreByStoreId(storeId);
        reservation.setStore(store);

        //예약 날짜 유효성 검사 -> 오늘 기준 이전 날짜가 오면 에러 발생
        validateReservationDate(reservation.getReservationDate());

        //로그인한 유저의 정보를 reservation에 담기
        String loginUserEmail = AuthUtil.getCurrentMemberEmail();
        Member member = memberService.findMemberByEmail(loginUserEmail);
        reservation.setMember(member);

        //reqReservation에 있는 상품 id가 존재하는지 확인 후 reservationItem 저장
        //List<ReservationItem> saveReservationItems = createReservationItem(reservationItems);
        reservation.setReservationItems(reservationItems);

        //예약 정보 저장
        reservationRepository.save(reservation);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateReservation(Long reservationId, Reservation updateReservation) {
        //수정할 예약 정보 조회 찾기
        Reservation findReservation = findReservation(reservationId);

        //로그인한 회원 정보와 reservation의 member와 동일한지 검증
        verifyReservationMember(findReservation.getMember());

        Optional.ofNullable(updateReservation.getReservationName())
                .ifPresent(findReservation::setReservationName);
        Optional.ofNullable(updateReservation.getReservationPhone())
                .ifPresent(findReservation::setReservationPhone);
        Optional.ofNullable(updateReservation.getReservationEmail())
                .ifPresent(findReservation::setReservationEmail);

        reservationRepository.save(findReservation);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void cancelReservation(Long reservationId) {
        Reservation findReservation = findReservation(reservationId);

        //로그인한 회원 정보와 reservation의 member와 동일한지 검증
        verifyReservationMember(findReservation.getMember());

        //예약 대기 -> 예약 취소
        findReservation.setReservationStatus(Reservation.ReservationStatus.RESERVATION_CANCLE);
        reservationRepository.delete(findReservation);

        //Todo 예약 취소 -> 환불
    }

    // 로그인한 회원과 reservation member 일치하는지 확인
    private void verifyReservationMember(Member reservationMember) {
        String loginUserEmail = AuthUtil.getCurrentMemberEmail();
        Member findMember = memberService.findMemberByEmail(loginUserEmail);

        if (!findMember.equals(reservationMember)) {
            throw new BusinessLogicException(ExceptionCode.RESERVATION_MEMBER_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public Reservation getReservations(Long reservationId) {
        Reservation reservation = findReservation(reservationId);
        return reservation;
    }

    //예약 찾기
    private Reservation findReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.RESERVATION_NOT_FOUND));
    }

    // 예약 날짜 검증 메서드
    private void validateReservationDate(LocalDate reservationDate) {
        if (reservationDate != null) {
            LocalDate today = LocalDate.now();
            if (reservationDate.isBefore(today)) {
                throw new IllegalArgumentException("예약 날짜는 이전 날짜에 적용할 수 없습니다.");
            }
        }
    }

    //예약 상품 생성 및 저장 메서드
    private List<ReservationItem> createReservationItem(Reservation reservation) {
        List<ReservationItem> saveReservationItemList = new ArrayList<>();
        List<ReservationItem> reservationItemList = reservation.getReservationItems();

        for (ReservationItem reservationItem : reservationItemList) {
            Long itemId = reservationItem.getItem().getItemId();
            //itemId로 해당 상품을 찾았음
            Item item = itemRepository.findById(itemId).orElseThrow(() -> new IllegalArgumentException("해당 상품이 존재하지 않습니다."));
            //티켓 valid 검사
            item.validateTicketCount(reservationItem.getTicketCount());

            //ReservationItem 저장
            ReservationItem saveReservationItem = new ReservationItem();
            saveReservationItem.setTicketCount(reservationItem.getTicketCount());
            saveReservationItem.setItem(item);
            saveReservationItem.setReservation(reservation);

            saveReservationItemList.add(saveReservationItem);
            //예약상품 저장
            reservationItemRepository.save(saveReservationItem);
        }
        //총 금액과 sumTicketCount의 가격이 동일한지 검증
        validateTotalPrice(saveReservationItemList, reservation.getTotalPrice());

        return saveReservationItemList;
    }

    //예약 금액 검증 메서드
    private void validateTotalPrice(List<ReservationItem> reservationItems, int totalPrice) {
        int sumTicketCount = 0;
        for (ReservationItem reservationItem : reservationItems) {
            sumTicketCount += reservationItem.getTicketCount() * reservationItem.getItem().getPrice();
        }
        if (totalPrice != sumTicketCount) {
            throw new IllegalArgumentException("예약하신 총 금액과 각 티켓 값의 총 금액이 일치하지 않습니다.");
        }
    }

    public Map<Long, Integer> reservationTicketCount(Store store, LocalDate currentDate) throws BusinessLogicException {
        //Todo 예약내역에서 1. 날짜,예약상태로 필터링 한 예약 정보들 // 그러면 조건에 맞는 예약들이 나옴 //
        //선택한 업체와 날짜의 예약들을 가져옴
        List<Reservation> currentDateReservations =
                reservationRepository.findByReservationDateAndStore(currentDate, store);
        Map<Long, Integer> remainingTicketInfo = new HashMap<>();
        for (Reservation reservation : currentDateReservations) {
            List<ReservationItem> reservationItems = reservation.getReservationItems();
            for (ReservationItem reservationItem : reservationItems) {
                long itemId = reservationItem.getItem().getItemId();
                int ticketCount = reservationItem.getTicketCount();// 해당 아이템의 예약한 티켓 수
                // 주석 부분 수정
                if (remainingTicketInfo.containsKey(itemId)) {
                    int existingTicketCount = remainingTicketInfo.get(itemId);
                    remainingTicketInfo.put(itemId, existingTicketCount + ticketCount);
                } else {
                    remainingTicketInfo.put(itemId, ticketCount);
                }
            }
        }
        return remainingTicketInfo;
    }

    public List<ItemDto> findItemsByStoreIdAndDate(long storeId, LocalDate date) {
        try{
            //Todo 예외처리하기
            List<ItemDto> itemDtos = new ArrayList<>();
            Store findStore = storeService.findStoreByStoreId(storeId);
            List<Item> findItems = findStore.getItems();
            Map<Long,Integer> reservationTickets = reservationTicketCount(findStore,date);
            for (Item item : findItems) {
                int reservationTicketCount = // 예약된 티켓이 없다면 null로 나오므로, null과 int는 연산이 불가능하므로, int로 변환
                        reservationTickets.containsKey(item.getItemId())
                                ? reservationTickets.get(item.getItemId()) : 0;

                int remainingTicketCount = item.getTotalTicket()-reservationTicketCount;
                if (remainingTicketCount <0) remainingTicketCount=0;
                ItemDto itemDto = new ItemDto(
                        item.getItemId(),
                        item.getItemName(),
                        item.getTotalTicket(),
                        item.getPrice(),
                        remainingTicketCount
                );
                itemDtos.add(itemDto);
            }
            return itemDtos;
        }catch (Exception e) {
            return null;
        }
    }
}