package fu.de191056.reservation.service.impl;

import feign.FeignException;
import fu.de191056.reservation.common.ReservationConstants;
import fu.de191056.reservation.dto.*;
import fu.de191056.reservation.entity.Reservation;
import fu.de191056.reservation.entity.ReservationStatus;
import fu.de191056.reservation.repository.ReservationRepository;
import fu.de191056.reservation.repository.RoomClient;
import fu.de191056.reservation.service.ReservationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomClient roomClient;
    private final ObjectMapper objectMapper;

    public ReservationServiceImpl(ReservationRepository reservationRepository, RoomClient roomClient,
                                  ObjectMapper objectMapper) {
        this.reservationRepository = reservationRepository;
        this.roomClient = roomClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ApiResponseDTO createReservation(ReservationDTO dto) {
        // checkOutDate must be after checkInDate (status 2 -> 406)
        if (!dto.getCheckOutDate().after(dto.getCheckInDate())) {
            return ApiResponseDTO.error(2, "checkOutDate must be after checkInDate");
        }

        // Check the room via RoomService (OpenFeign)
        RoomDTO room = fetchRoom(dto.getRoomId());
        if (room == null) {
            return ApiResponseDTO.error(4, "Room ID is not found");                      // 404
        }
        if (!ReservationConstants.ROOM_AVAILABLE.equals(room.getStatus())) {
            return ApiResponseDTO.error(5, "Room is not AVAILABLE for reservation");      // 400
        }
        if (dto.getNumberOfGuests() > room.getCapacity()) {
            return ApiResponseDTO.error(2, "numberOfGuests must not exceed the room capacity"); // 406
        }

        Reservation r = new Reservation();
        r.setGuestName(dto.getGuestName());
        r.setGuestEmail(dto.getGuestEmail());
        r.setGuestPhone(dto.getGuestPhone());
        r.setCheckInDate(dto.getCheckInDate());
        r.setCheckOutDate(dto.getCheckOutDate());
        r.setNumberOfGuests(dto.getNumberOfGuests());
        r.setRoomId(dto.getRoomId());
        r.setTotalAmount(computeTotal(room.getPricePerNight(), dto.getCheckInDate(), dto.getCheckOutDate()));
        // Default status is CONFIRMED when not provided
        r.setStatus(dto.getStatus() == null ? ReservationStatus.CONFIRMED : dto.getStatus());

        Reservation saved = reservationRepository.save(r);
        return ApiResponseDTO.ok("Reservation created successfully", toDTO(saved));
    }

    @Override
    @Transactional
    public ApiResponseDTO updateReservation(Long reservationId, ReservationDTO dto) {
        Reservation r = reservationRepository.findById(reservationId).orElse(null);
        if (r == null) {
            return ApiResponseDTO.error(4, "Reservation ID is not found");
        }
        // Validate the EFFECTIVE values BEFORE touching the managed entity:
        // mutating it and returning early would still let dirty-checking flush bad values.
        Date checkIn = dto.getCheckInDate() != null ? dto.getCheckInDate() : r.getCheckInDate();
        Date checkOut = dto.getCheckOutDate() != null ? dto.getCheckOutDate() : r.getCheckOutDate();
        if (!checkOut.after(checkIn)) {
            return ApiResponseDTO.error(2, "checkOutDate must be after checkInDate");
        }
        Long roomId = dto.getRoomId() != null ? dto.getRoomId() : r.getRoomId();
        int guests = dto.getNumberOfGuests() != null ? dto.getNumberOfGuests() : r.getNumberOfGuests();

        RoomDTO room = fetchRoom(roomId);
        if (room == null) {
            return ApiResponseDTO.error(4, "Room ID is not found");
        }
        // Moving to another room requires that room to be AVAILABLE
        if (!Objects.equals(roomId, r.getRoomId())
                && !ReservationConstants.ROOM_AVAILABLE.equals(room.getStatus())) {
            return ApiResponseDTO.error(2, "Room is not AVAILABLE for reservation");
        }
        if (guests > room.getCapacity()) {
            return ApiResponseDTO.error(2, "numberOfGuests must not exceed the room capacity");
        }

        // Partial update (safe now that the effective values are validated)
        if (dto.getGuestName() != null)  r.setGuestName(dto.getGuestName());
        if (dto.getGuestEmail() != null) r.setGuestEmail(dto.getGuestEmail());
        if (dto.getGuestPhone() != null) r.setGuestPhone(dto.getGuestPhone());
        if (dto.getStatus() != null)     r.setStatus(dto.getStatus());
        r.setCheckInDate(checkIn);
        r.setCheckOutDate(checkOut);
        r.setNumberOfGuests(guests);
        r.setRoomId(roomId);
        r.setTotalAmount(computeTotal(room.getPricePerNight(), checkIn, checkOut));

        Reservation saved = reservationRepository.save(r);
        return ApiResponseDTO.ok("Reservation updated successfully", toDTO(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO getReservationById(Long reservationId) {
        Reservation r = reservationRepository.findById(reservationId).orElse(null);
        if (r == null) {
            return ApiResponseDTO.error(4, "Reservation is not found");
        }
        return ApiResponseDTO.ok("Successful", toDetailDTO(r));
    }

    @Override
    @Transactional
    public ApiResponseDTO cancelReservation(Long reservationId) {
        Reservation r = reservationRepository.findById(reservationId).orElse(null);
        if (r == null) {
            return ApiResponseDTO.error(4, "Reservation is not found");
        }
        r.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(r);
        return ApiResponseDTO.ok("Reservation cancelled successfully", null);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO getReservationList(String guestName, ReservationStatus status, Pageable pageable) {
        String name = (guestName == null || guestName.isBlank()) ? null : guestName;
        Page<Reservation> page = reservationRepository.search(name, status, pageable);
        // ponytail: one RoomService call per row (max 100); add a batch endpoint if lists get slow
        List<ReservationDetailDTO> content = page.getContent().stream().map(this::toDetailDTO).toList();

        PageDTO pageDTO = new PageDTO(
                page.getSize(), page.getNumber(), page.getTotalPages(),
                page.getTotalElements(), page.isFirst(), page.isLast(), content);
        return ApiResponseDTO.ok("Successful", pageDTO);
    }

    // -- helpers --

    /** Calls RoomService via OpenFeign; returns null when the room does not exist (HTTP 404). */
    private RoomDTO fetchRoom(Long roomId) {
        try {
            ApiResponseDTO res = roomClient.getRoomById(roomId);
            if (res == null || res.getStatus() != 1 || res.getData() == null) {
                return null;
            }
            return objectMapper.convertValue(res.getData(), RoomDTO.class);
        } catch (FeignException.NotFound ex) {
            return null;
        }
        // Other FeignExceptions (503, timeout...) propagate -> GlobalExceptionHandler -> 500/status 0
    }

    private BigDecimal computeTotal(BigDecimal pricePerNight, Date checkIn, Date checkOut) {
        long nights = ChronoUnit.DAYS.between(toLocalDate(checkIn), toLocalDate(checkOut));
        return pricePerNight.multiply(BigDecimal.valueOf(nights));
    }

    private LocalDate toLocalDate(Date date) {
        // java.sql.Date (loaded from DB) does not support toInstant()
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private ReservationDTO toDTO(Reservation r) {
        ReservationDTO d = new ReservationDTO();
        d.setReservationId(r.getReservationId());
        d.setGuestName(r.getGuestName());
        d.setGuestEmail(r.getGuestEmail());
        d.setGuestPhone(r.getGuestPhone());
        d.setCheckInDate(r.getCheckInDate());
        d.setCheckOutDate(r.getCheckOutDate());
        d.setNumberOfGuests(r.getNumberOfGuests());
        d.setTotalAmount(r.getTotalAmount());
        d.setStatus(r.getStatus());
        d.setRoomId(r.getRoomId());
        return d;
    }

    private ReservationDetailDTO toDetailDTO(Reservation r) {
        ReservationDetailDTO d = new ReservationDetailDTO();
        d.setReservationId(r.getReservationId());
        d.setGuestName(r.getGuestName());
        d.setGuestEmail(r.getGuestEmail());
        d.setGuestPhone(r.getGuestPhone());
        d.setCheckInDate(r.getCheckInDate());
        d.setCheckOutDate(r.getCheckOutDate());
        d.setNumberOfGuests(r.getNumberOfGuests());
        d.setTotalAmount(r.getTotalAmount());
        d.setStatus(r.getStatus());
        d.setRoom(fetchRoom(r.getRoomId()));
        return d;
    }
}
