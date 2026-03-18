package com.example.demo.graphql.query

import com.example.demo.service.DashboardData
import com.example.demo.service.DashboardService
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller

@Controller
class DashboardQueryController(private val dashboardService: DashboardService) {

    @QueryMapping
    fun dashboard(@AuthenticationPrincipal userDetails: UserDetails): DashboardData {
        return dashboardService.getDashboardData(userDetails.username)
    }
}
