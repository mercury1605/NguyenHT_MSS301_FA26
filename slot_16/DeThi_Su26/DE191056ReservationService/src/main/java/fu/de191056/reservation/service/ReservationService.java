package fu.de191056.reservation.service;

import fu.de191056.reservation.dto.ApiResponseDTO;
import fu.de191056.reservation.dto.ReservationDTO;
import fu.de191056.reservation.entity.ReservationStatus;
import org.springframework.data.domain.Pageable;

public interface ReservationService {
    ApiResponseDTO createReservation(ReservationDTO dto);
    ApiResponseDTO updateReservation(Long reservationId, ReservationDTO dto);
    ApiResponseDTO getReservationById(Long reservationId);
    ApiResponseDTO cancelReservation(Long reservationId);
    ApiResponseDTO getReservationList(String guestName, ReservationStatus status, Pageable pageable);
}
