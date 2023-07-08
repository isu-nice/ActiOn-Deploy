package actiOn.Img.profileImg;

import actiOn.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileImgRepository extends JpaRepository<ProfileImg, Long> {
    Optional<ProfileImg> findByMember(Member member);
}