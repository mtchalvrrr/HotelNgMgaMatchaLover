package com.mtchlvr.hotel.repo;
import com.mtchlvr.hotel.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByActiveTrueOrderByTypeAscRoomNumberAsc();
    List<Room> findByActiveTrueAndAvailableTrueOrderByTypeAscRoomNumberAsc();
    boolean existsByRoomNumber(String roomNumber);
}
