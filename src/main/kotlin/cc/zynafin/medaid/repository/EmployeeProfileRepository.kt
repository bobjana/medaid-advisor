package cc.zynafin.medaid.repository

import cc.zynafin.medaid.domain.EmployeeProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EmployeeProfileRepository : JpaRepository<EmployeeProfile, UUID>
