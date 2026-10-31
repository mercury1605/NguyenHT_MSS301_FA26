package fu.de191056.reservation.controller;

import fu.de191056.reservation.common.OnCreate;
import fu.de191056.reservation.dto.ApiResponseDTO;
import fu.de191056.reservation.dto.ReservationDTO;
import fu.de191056.reservation.entity.ReservationStatus;
import fu.de191056.reservation.service.ReservationService;
import jakarta.validation.groups.Default;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class DE191056ReservationController {
    private final ReservationService reservationService;

    public DE191056ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // 1. Create Reservation -> 201/1, 406/2, 404/4, 400/5, 500/0
    @PostMapping
    public ResponseEntity<ApiResponseDTO> create(@Validated({Default.class, OnCreate.class}) @RequestBody ReservationDTO dto,
                                                 BindingResult br) {
        if (br.hasErrors()) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE) // 406
                    .body(ApiResponseDTO.error(2, firstError(br)));
        }
        return map(reservationService.createReservation(dto), HttpStatus.CREATED); // 201 on success
    }

    // 2. Update Reservation (partial) -> 200/1, 406/2, 404/4, 500/0
    @PutMapping("/{reservationId}")
    public ResponseEntity<ApiResponseDTO> update(@PathVariable Long reservationId,
                                                 @Validated @RequestBody ReservationDTO dto,
                                                 BindingResult br) {
        if (br.hasErrors()) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE) // 406
                    .body(ApiResponseDTO.error(2, firstError(br)));
        }
        return map(reservationService.updateReservation(reservationId, dto), HttpStatus.OK);
    }

    // 3. Get Reservation Detail -> 200/1, 404/4, 500/0
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponseDTO> getDetail(@PathVariable Long reservationId) {
        return map(reservationService.getReservationById(reservationId), HttpStatus.OK);
    }

    // 4. Cancel Reservation (delete) -> 200/1, 404/4, 500/0
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ApiResponseDTO> cancel(@PathVariable Long reservationId) {
        return map(reservationService.cancelReservation(reservationId), HttpStatus.OK);
    }

    // 5. Get Reservation List -> 200/1, 406/2, 500/0
    @GetMapping
    public ResponseEntity<ApiResponseDTO> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String guestName,
                                               @RequestParam(required = false) ReservationStatus status) {
        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE) // 406
                    .body(ApiResponseDTO.error(2, "page must be >= 0 and size must be between 1 and 100"));
        }
        return map(reservationService.getReservationList(guestName, status, PageRequest.of(page, size)), HttpStatus.OK);
    }

    private String firstError(BindingResult br) {
        return br.getAllErrors().isEmpty() ? "Validation failed" : br.getAllErrors().get(0).getDefaultMessage();
    }

    // map service status -> HTTP code
    private ResponseEntity<ApiResponseDTO> map(ApiResponseDTO res, HttpStatus okStatus) {
        HttpStatus httpStatus = switch (res.getStatus()) {
            case 1 -> okStatus;
            case 2 -> HttpStatus.NOT_ACCEPTABLE;   // 406 validation error
            case 4 -> HttpStatus.NOT_FOUND;        // 404 reservation / room not found
            case 5 -> HttpStatus.BAD_REQUEST;      // 400 room not AVAILABLE
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(httpStatus).body(res);
    }
}
