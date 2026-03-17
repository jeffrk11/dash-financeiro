package com.jeff.resource;

import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import com.jeff.service.DashboardService;

@Path("/dashboard")
public class DashboardResource {

    @Inject
    Template dashboard;

    @Inject
    DashboardService service;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String dashboard(@QueryParam("month") String month) {
        return dashboard.data("data", service.getDashboard(month)).render();
    }
}

