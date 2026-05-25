package com.postgre.etims.controller;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // allow React
public class EtimsReportController {

	@Autowired
	private JdbcTemplate jdbcTemplate;
	@PersistenceContext
	private EntityManager entityManager;

	// =====================================
	// ✅ DROPDOWN API
	// =====================================
	@GetMapping("/dropdowns")
	public Map<String, Object> getDropdowns(@RequestParam(required = false) String zone,
			@RequestParam(required = false) String circle) {

		Map<String, Object> response = new HashMap<>();

		// ============================
		// ZONES
		// ============================

		List<Map<String, Object>> zones = jdbcTemplate.queryForList(
				"SELECT DISTINCT zone " + "FROM analytical_dashboard.taxpayer_details " + "ORDER BY zone");

		// ============================
		// CIRCLES
		// ============================

		String circleQuery = "SELECT DISTINCT circle " + "FROM analytical_dashboard.taxpayer_details " + "WHERE 1=1";

		List<Object> circleParams = new ArrayList<>();

		if (zone != null && !zone.isBlank() && !zone.equalsIgnoreCase("All Zones")) {

			circleQuery += " AND zone = ?";
			circleParams.add(zone);
		}

		circleQuery += " ORDER BY circle";

		List<Map<String, Object>> circles = jdbcTemplate.queryForList(circleQuery, circleParams.toArray());

		// ============================
		// UNITS
		// ============================

		String unitQuery = "SELECT DISTINCT unit " + "FROM analytical_dashboard.taxpayer_details " + "WHERE 1=1";

		List<Object> unitParams = new ArrayList<>();

		if (zone != null && !zone.isBlank() && !zone.equalsIgnoreCase("All Zones")) {

			unitQuery += " AND zone = ?";
			unitParams.add(zone);
		}

		if (circle != null && !circle.isBlank() && !circle.equalsIgnoreCase("All Circles")) {

			unitQuery += " AND circle = ?";
			unitParams.add(circle);
		}

		unitQuery += " ORDER BY unit";

		List<Map<String, Object>> units = jdbcTemplate.queryForList(unitQuery, unitParams.toArray());

		// ============================
		// RESPONSE
		// ============================

		response.put("zones", zones);
		response.put("circles", circles);
		response.put("units", units);

		return response;
	}

	// =====================================
	// ✅ SEARCH API (GSTR3B GSTIN WISE)
	// =====================================
	@GetMapping("/gstr3bSearchResults")
	public List<Map<String, Object>> searchReportGSTIN(@RequestParam(required = false) String zone,
			@RequestParam(required = false) String unit, @RequestParam(required = false) String circle,
			@RequestParam(required = false) String gstin, @RequestParam(required = false) String month,
			@RequestParam(required = false) String year) {

		String query = """
				    SELECT
				        g.gstin,
				        t.trdnm AS name,
				        t.appr_auth AS authority,
				        t.zone,
				        t.unit,
				        t.circle,

				        COALESCE(txval_osup_det,0)  as turnover,

				        COALESCE(iamt_osup_det,0) as igst,
				        COALESCE(camt_osup_det,0) as cgst,
				        COALESCE(samt_osup_det,0) as sgst,

				        COALESCE(csamt_osup_det,0) as cess,

				        COALESCE(g.iamt_isup_rev,0) as igst_rcm,
				        COALESCE(g.camt_isup_rev,0) as cgst_rcm,
				        COALESCE(g.samt_isup_rev,0) as sgst_rcm,
				        COALESCE(g.csamt_isup_rev,0) as cess_rcm,

				        COALESCE(g.iamt_itc_net_itc_elg,0) as igst_itc,
				        COALESCE(g.camt_itc_net_itc_elg,0) as cgst_itc,
				        COALESCE(g.samt_itc_net_itc_elg,0) as sgst_itc,
				        COALESCE(g.csamt_itc_net_itc_elg,0) as cess_itc

				    FROM gst_api_gstr_3b."GSTR3B_RETURN" g
				    LEFT JOIN analytical_dashboard."taxpayer_details" t
				        ON g.gstin = t.gstin
				    WHERE 1=1
				""";

		List<Object> params = new ArrayList<>();

		// ✅ FILTERS
		if (zone != null && !zone.equals("All Zones")) {
			query += " AND t.zone = ?";
			params.add(zone);
		}

		if (unit != null && !unit.equals("All Units")) {
			query += " AND t.unit = ?";
			params.add(unit);
		}

		if (circle != null && !circle.equals("All Circles")) {
			query += " AND t.circle = ?";
			params.add(circle);
		}

		if (gstin != null && !gstin.trim().isEmpty()) {
			query += " AND g.gstin = ?";
			params.add(gstin);
		}

		// OPTIONAL: If you have month/year column add here
		// Example:
		// if (month != null && !month.isEmpty()) {
		// query += " AND g.month = ?";
		// params.add(month);
		// }

		// if (year != null && !year.isEmpty()) {
		// query += " AND g.year = ?";
		// params.add(year);
		// }

		query += " LIMIT 10";

		System.out.println("QUERY: " + query);
		System.out.println("PARAMS: " + params);

		return jdbcTemplate.queryForList(query, params.toArray());
	}

	@GetMapping("/topTaxpayers")
	public List<Map<String, Object>> searchTopTaxpayersStateGST(@RequestParam(required = false) String zone,
			@RequestParam(required = false) String unit, @RequestParam(required = false) String circle) {
		System.out.println("inside topTaxpayers");
		String sql = """
				    WITH fy AS (
				        SELECT
				            MAKE_DATE(
				                EXTRACT(YEAR FROM CURRENT_DATE)::int
				                - CASE WHEN EXTRACT(MONTH FROM CURRENT_DATE) >= 4 THEN 1 ELSE 2 END,
				                4, 1
				            ) AS start_date,
				            MAKE_DATE(
				                EXTRACT(YEAR FROM CURRENT_DATE)::int
				                - CASE WHEN EXTRACT(MONTH FROM CURRENT_DATE) >= 4 THEN 0 ELSE 1 END,
				                3, 31
				            ) AS end_date
				    ),
				    fy_data AS (
				        SELECT
				            g.gstin,
				            SUM(
				                COALESCE(g.iamt_osup_det,0) +
				                COALESCE(g.camt_osup_det,0) +
				                COALESCE(g.samt_osup_det,0) +
				                COALESCE(g.csamt_osup_det,0)
				            ) AS total_tax_liability
				        FROM gst_api_gstr_3b."GSTR3B_RETURN" g
				        JOIN fy f ON TRUE
				        WHERE TO_DATE(g.ret_period, 'MMYYYY')
				              BETWEEN f.start_date AND f.end_date
				        GROUP BY g.gstin
				    ),
				    last_month_return_filed AS (
				        SELECT *
				        FROM (
				            SELECT
				                gstin,
				                ret_period,
				                fil_dt,
				                SUM(
				                    COALESCE(iamt_osup_det,0) +
				                    COALESCE(camt_osup_det,0) +
				                    COALESCE(samt_osup_det,0) +
				                    COALESCE(csamt_osup_det,0)
				                ) AS tax_liability,
				                ROW_NUMBER() OVER (
				                    PARTITION BY gstin
				                    ORDER BY fil_dt DESC NULLS LAST
				                ) AS rn
				            FROM gst_api_gstr_3b."GSTR3B_RETURN"
				            WHERE fil_dt IS NOT NULL
				            GROUP BY gstin, ret_period, fil_dt
				        ) x
				        WHERE rn = 1
				    ),
				    latest_return AS (
				        SELECT
				            g.gstin,
				            g.ret_period,
				            g.fil_dt,
				            CASE
				                WHEN g.fil_dt IS NOT NULL THEN 'Y'
				                ELSE 'N'
				            END AS latest_return_filed_flag
				        FROM gst_api_gstr_3b."GSTR3B_RETURN" g
				        WHERE g.ret_period = TO_CHAR(
				            DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '1 month',
				            'MMYYYY'
				        )
				    )
				    SELECT
				        f.gstin,
				        t.trdnm AS legal_name,
				        t.circle,
				        m.filing_typ,
				        COALESCE(lr.latest_return_filed_flag, 'N') AS latest_return_filed_flag,
				        CASE
				            WHEN lr.latest_return_filed_flag = 'Y' THEN lr.ret_period
				            ELSE lm.ret_period
				        END AS last_return_filed,
				        CASE
				            WHEN lr.latest_return_filed_flag = 'Y' THEN lr.fil_dt
				            ELSE lm.fil_dt
				        END AS last_filing_date,
				        lm.tax_liability
				    FROM fy_data f
				    LEFT JOIN last_month_return_filed lm ON f.gstin = lm.gstin
				    LEFT JOIN latest_return lr ON f.gstin = lr.gstin
				    LEFT JOIN analytical_dashboard."taxpayer_details" t ON f.gstin = t.gstin
				    LEFT JOIN analytical_dashboard."non_filer_dashboard_new" m ON f.gstin = m.gstin
				    WHERE f.total_tax_liability > 0
				    ORDER BY t.circle ASC, f.total_tax_liability DESC
				    LIMIT 20
				""";

		return jdbcTemplate.queryForList(sql);
	}

	@Autowired
	private NamedParameterJdbcTemplate namedJdbcTemplate;

	@GetMapping("/defaulter3b")
	public ResponseEntity<?> get3BDefaulter(

	        @RequestParam(required = false) String zone,

	        @RequestParam(required = false) String unit,

	        @RequestParam(required = false) String circle,

	        @RequestParam(required = false) String filingType,

	        @RequestParam(required = false) String auth,

	        @RequestParam(required = false) String status,

	        @RequestParam(required = false) String defaultMonths,

	        @RequestParam(required = false) String filerStatus

	) {

	    try {

	        System.out.println("====================================");
	        System.out.println("INSIDE GSTR3B DEFAULTER API");
	        System.out.println("====================================");

	        /* =========================================
	           HANDLE DEFAULT + SELECTED VALUES
	        ========================================= */

	        zone          = normalize(zone);
	        unit          = normalize(unit);
	        circle        = normalize(circle);
	        filingType    = normalize(filingType);
	        auth          = normalize(auth);
	        status        = normalize(status);
	        defaultMonths = normalize(defaultMonths);
	        filerStatus   = normalizeFiler(filerStatus);

	        /* =========================================
	           DEBUG LOGS
	        ========================================= */

	        System.out.println("ZONE           => " + zone);
	        System.out.println("UNIT           => " + unit);
	        System.out.println("CIRCLE         => " + circle);
	        System.out.println("FILING TYPE    => " + filingType);
	        System.out.println("AUTHORITY      => " + auth);
	        System.out.println("STATUS         => " + status);
	        System.out.println("DEFAULT MONTHS => " + defaultMonths);
	        System.out.println("FILER STATUS   => " + filerStatus);

	        /* =========================================
	           SQL FUNCTION CALL
	        ========================================= */

	        String sql =
	                "SELECT * FROM analytical_dashboard.get_mis_table(?,?,?,?,?,?,?,?) LIMIT 10";

	        List<Map<String, Object>> result =
	                jdbcTemplate.queryForList(

	                        sql,

	                        zone,
	                        unit,
	                        circle,
	                        filingType,
	                        auth,
	                        status,
	                        defaultMonths,
	                        filerStatus

	                );

	        System.out.println("TOTAL RECORDS => " + result.size());

	        return ResponseEntity.ok(result);

	    } catch (Exception e) {

	        e.printStackTrace();

	        Map<String, Object> error = new HashMap<>();

	        error.put("message", "FAILED");

	        error.put("error", e.getMessage());

	        return ResponseEntity.status(500).body(error);
	    }
	}


	/* =========================================================
	   NORMALIZE FUNCTION
	   Handles:
	   - Default dropdown values
	   - Actual selected values
	========================================================= */

	private String normalize(String value) {

	    if (value == null) {
	        return null;
	    }

	    value = value.trim();

	    /* =========================================
	       HANDLE DEFAULT DROPDOWN VALUES
	    ========================================= */

	    if (

	            value.isEmpty()

	            || value.equalsIgnoreCase("ALL")

	            || value.equalsIgnoreCase("All")

	            || value.equalsIgnoreCase("All Zones")

	            || value.equalsIgnoreCase("All Units")

	            || value.equalsIgnoreCase("All Circles")

	            || value.equalsIgnoreCase("-- All --")

	            || value.equalsIgnoreCase("-- Select Report Type --")

	            || value.equalsIgnoreCase("Select")

	            || value.equalsIgnoreCase("NA")

	    ) {

	        return null;
	    }

	    /* =========================================
	       HANDLE NORMAL SELECTED VALUE
	    ========================================= */

	    return value;
	}


	/* =========================================================
	   FILER STATUS NORMALIZE
	========================================================= */

	private String normalizeFiler(String value) {

	    if (value == null) {
	        return null;
	    }

	    value = value.trim();

	    /* =========================================
	       HANDLE DEFAULT VALUES
	    ========================================= */

	    if (

	            value.isEmpty()

	            || value.equalsIgnoreCase("ALL")

	            || value.equalsIgnoreCase("-- All --")

	            || value.equalsIgnoreCase("All")

	    ) {

	        return null;
	    }

	    /* =========================================
	       HANDLE VALID VALUES
	    ========================================= */

	    if ("0".equals(value) || "1".equals(value)) {

	        return value;
	    }

	    return null;
	}

	@GetMapping("/taxPayerList")
	public List<Map<String, Object>> getTaxPayerList(@RequestParam(required = false) String zone,
			@RequestParam(required = false) String unit, @RequestParam(required = false) String circle,
			@RequestParam(required = false) String status) {

		List<Map<String, Object>> result = new ArrayList<>();
		System.out.println("inside taxPayerList" + status);
		String sql = """

				    SELECT
				        a.gstin,

				        a.trdnm AS trade_name,

				        a.appr_auth AS approving_authority,

				        a.cobz AS constitution_of_business,

				        a.ntbz AS nature_of_business,

				        a.authstatus AS status,

				        a.circle,

				        z."Range" AS range,

				        a.apprvdt AS date_of_registration,

				        a.ppbz_address AS principal_address,

				        CASE
				            WHEN a.regtypecd = 'APLTC' THEN 'TCS Taxpayer'
				            WHEN a.regtypecd = 'APLTD' THEN 'TDS Taxpayer'
				            WHEN a.regtypecd = 'CA' THEN 'Casual Taxpayer'
				            WHEN a.regtypecd = 'ID' THEN 'ID'
				            WHEN a.regtypecd = 'CO' THEN 'Composition Taxpayer'
				            WHEN a.regtypecd = 'NT' THEN 'Normal Taxpayer'
				            WHEN a.regtypecd = 'TP' THEN 'Normal Taxpayer'
				            ELSE 'Other'
				        END AS taxpayer_type,

				        a.ntcrbs AS core_business_activity

				    FROM analytical_dashboard.reg_details_ayush a

				    LEFT JOIN gst_api_registration.zone_unit_circle_mapping z
				    ON a.circle = z.circle

				    WHERE
				    (
				        :circle IS NULL
				        OR :circle = 'All Circles'
				        OR a.circle = :circle
				    )

				    AND
				    (
				        (:status = 'ACTIVE'
				            AND a.authstatus = 'Active')

				        OR

				        (:status = 'SUSPENDED'
				            AND a.authstatus ILIKE '%Suspended%')

				        OR

				        (:status = 'CANCELLED'
				            AND (
				                a.authstatus ILIKE '%Cancelled%'
				                OR a.authstatus ILIKE '%Canceled%'
				            )
				        )

				        OR

				        (:status = 'ALL')

				        OR

				        (:status IS NULL)
				    )



				""";

		Query query = entityManager.createNativeQuery(sql);

		query.setParameter("circle", circle);
		query.setParameter("status", status);

		List<Object[]> rows = query.getResultList();

		for (Object[] row : rows) {

			Map<String, Object> map = new HashMap<>();

			map.put("gstin", row[0]);
			map.put("tradeName", row[1]);
			map.put("approvingAuthority", row[2]);
			map.put("constitutionOfBusiness", row[3]);
			map.put("natureOfBusiness", row[4]);
			map.put("status", row[5]);
			map.put("circle", row[6]);
			map.put("range", row[7]);
			map.put("dateOfRegistration", row[8]);
			map.put("principalAddress", row[9]);
			map.put("taxpayerType", row[10]);
			map.put("coreBusinessActivity", row[11]);

			result.add(map);
		}

		return result;
	}

	@GetMapping("/igstreversal")
	public ResponseEntity<?> igstReversalReport(

			@RequestParam(required = false) String zone,

			@RequestParam(required = false) String unit,

			@RequestParam(required = false) String circle,

			@RequestParam(required = false) String authority,

			@RequestParam(required = false) String status,

			@RequestParam(required = false) String taxType,

			@RequestParam(required = false) BigDecimal reversalMin,

			@RequestParam(required = false) BigDecimal reversalMax,

			@RequestParam(required = false) BigDecimal ceMin,

			@RequestParam(required = false) BigDecimal ceMax

	) {

		try {

			System.out.println("========= IGST REVERSAL =========");

			System.out.println("zone = " + zone);

			System.out.println("unit = " + unit);

			System.out.println("circle = " + circle);

			System.out.println("authority = " + authority);

			System.out.println("status = " + status);

			System.out.println("taxType = " + taxType);

			System.out.println("reversalMin = " + reversalMin);

			System.out.println("reversalMax = " + reversalMax);

			System.out.println("ceMin = " + ceMin);

			System.out.println("ceMax = " + ceMax);

			/*
			 * ========================= FUNCTION QUERY =========================
			 */

			String sql = """
					    SELECT *
					    FROM analytical_dashboard.get_igst_mis(
					        ?,
					        ?,
					        ?,
					        ?,
					        ?,
					        ?,
					        ?,
					        ?,
					        ?,
					        ?
					    )
					""";

			List<Map<String, Object>> result = jdbcTemplate.queryForList(

					sql,

					zone != null && !zone.equalsIgnoreCase("All Zones") ? zone : null,

					unit != null && !unit.equalsIgnoreCase("All Units") ? unit : null,

					circle != null && !circle.equalsIgnoreCase("All Circles") ? circle : null,

					authority != null && !authority.equalsIgnoreCase("All") ? authority : null,

					status != null && !status.equalsIgnoreCase("All") ? status : null,

					taxType != null && !taxType.equalsIgnoreCase("All") ? taxType : null,

					reversalMin,

					reversalMax,

					ceMin,

					ceMax);

			return ResponseEntity.ok(result);

		} catch (Exception e) {

			e.printStackTrace();

			return ResponseEntity.badRequest().body("Error : " + e.getMessage());
		}
	}

	@GetMapping("/rateOfFiling")
	public ResponseEntity<?> rate_Of_Gst_Filing(

			@RequestParam(required = false) String zone,

			@RequestParam(required = false) String unit,

			@RequestParam(required = false) String circle,

			@RequestParam(required = false) String year,

			@RequestParam(required = false) String ret_period) {

		try {

			System.out.println("inside rate_Of_Gst_Filing");

			System.out.println("ZONE => " + zone);
			System.out.println("UNIT => " + unit);
			System.out.println("CIRCLE => " + circle);
			System.out.println("YEAR => " + year);
			System.out.println("RETURN PERIOD => " + ret_period);

			String sql = """

					WITH active_taxpayers AS (

					    SELECT DISTINCT
					        t.gstin,
					        t.circle,
					        t.appr_auth,
					        t.zone,
					        t.unit

					    FROM analytical_dashboard.taxpayer_details t

					    WHERE t.authstatus = 'Active'
					      AND t.appr_auth IN ('STATE', 'CENTER')
					      AND t.circle <> 'TOTAL'

					      AND (? IS NULL OR t.zone = ?)
					      AND (? IS NULL OR t.unit = ?)
					      AND (? IS NULL OR t.circle = ?)
					),

					filed_taxpayers AS (

					    SELECT DISTINCT
					        g.gstin

					    FROM gst_api_gstr_3b."GSTR3B_RETURN" g

					    WHERE g.ret_period = ?
					)

					SELECT

					    COALESCE(a.circle, 'TOTAL') AS location,

					    COUNT(DISTINCT CASE
					        WHEN a.appr_auth = 'STATE'
					         AND f.gstin IS NULL
					        THEN a.gstin
					    END) AS state_to_be_filed,

					    COUNT(DISTINCT CASE
					        WHEN a.appr_auth = 'STATE'
					         AND f.gstin IS NOT NULL
					        THEN a.gstin
					    END) AS state_had_filed,

					    ROUND(

					        (
					            COUNT(DISTINCT CASE
					                WHEN a.appr_auth = 'STATE'
					                 AND f.gstin IS NOT NULL
					                THEN a.gstin
					            END)::numeric

					            /

					            NULLIF(
					                COUNT(DISTINCT CASE
					                    WHEN a.appr_auth = 'STATE'
					                    THEN a.gstin
					                END),
					                0
					            )

					        ) * 100,

					        2

					    ) AS state_rate_of_return_filing,

					    COUNT(DISTINCT CASE
					        WHEN a.appr_auth = 'CENTER'
					         AND f.gstin IS NULL
					        THEN a.gstin
					    END) AS center_to_be_filed,

					    COUNT(DISTINCT CASE
					        WHEN a.appr_auth = 'CENTER'
					         AND f.gstin IS NOT NULL
					        THEN a.gstin
					    END) AS center_had_filed,

					    ROUND(

					        (
					            COUNT(DISTINCT CASE
					                WHEN a.appr_auth = 'CENTER'
					                 AND f.gstin IS NOT NULL
					                THEN a.gstin
					            END)::numeric

					            /

					            NULLIF(
					                COUNT(DISTINCT CASE
					                    WHEN a.appr_auth = 'CENTER'
					                    THEN a.gstin
					                END),
					                0
					            )

					        ) * 100,

					        2

					    ) AS center_rate_of_return_filing,

					    COUNT(DISTINCT CASE
					        WHEN f.gstin IS NULL
					        THEN a.gstin
					    END) AS total_to_be_filed,

					    COUNT(DISTINCT CASE
					        WHEN f.gstin IS NOT NULL
					        THEN a.gstin
					    END) AS total_had_filed,

					    ROUND(

					        (
					            COUNT(DISTINCT CASE
					                WHEN f.gstin IS NOT NULL
					                THEN a.gstin
					            END)::numeric

					            /

					            NULLIF(
					                COUNT(DISTINCT a.gstin),
					                0
					            )

					        ) * 100,

					        2

					    ) AS total_rate_of_return_filing

					FROM active_taxpayers a

					LEFT JOIN filed_taxpayers f
					       ON a.gstin = f.gstin

					GROUP BY ROLLUP(a.circle)

					ORDER BY
					    CASE
					        WHEN a.circle IS NULL THEN 1
					        ELSE 0
					    END,
					    a.circle

					""";

			List<Map<String, Object>> result = jdbcTemplate.queryForList(

					sql,

					// zone
					zone != null && !zone.equalsIgnoreCase("All Zones") ? zone : null,

					zone != null && !zone.equalsIgnoreCase("All Zones") ? zone : null,

					// unit
					unit != null && !unit.equalsIgnoreCase("All Units") ? unit : null,

					unit != null && !unit.equalsIgnoreCase("All Units") ? unit : null,

					// circle
					circle != null && !circle.equalsIgnoreCase("All Circles") ? circle : null,

					circle != null && !circle.equalsIgnoreCase("All Circles") ? circle : null,

					// return period
					ret_period);

			return ResponseEntity.ok(result);

		} catch (Exception e) {

			e.printStackTrace();

			return ResponseEntity.badRequest().body("Error : " + e.getMessage());
		}

	}

	@GetMapping("/registrationstats")
	public ResponseEntity<?> getregstats(

			@RequestParam(required = false) String zone,

			@RequestParam(required = false) String unit,

			@RequestParam(required = false) String circle) {

		try {

			System.out.println("inside stats");

			String sql = """

					WITH taxpayer_data AS (

					    SELECT

					        zone,
					        unit,
					        circle,

					        /* =====================================
					           JURISDICTION
					        ===================================== */

					        CASE
					            WHEN UPPER(appr_auth) = 'STATE'
					            THEN 'STATE'

					            WHEN UPPER(appr_auth) IN ('CENTER', 'CENTRE')
					            THEN 'CENTER'

					            ELSE 'OTHER'
					        END AS jurisdiction,

					        /* =====================================
					           ACTIVE / CANCELLED
					        ===================================== */

					        CASE
					            WHEN authstatus = 'Active'
					            THEN 'ACTIVE'

					            WHEN authstatus IN (
					                'Cancelled',
					                'Cancelled suo-moto',
					                'Cancelled on Application of Taxpayer',
					                'Cancelled due to expiry of registration period'
					            )
					            THEN 'CANCELLED'

					            ELSE 'OTHER'
					        END AS taxpayer_status,

					        /* =====================================
					           REGISTRATION TYPE
					        ===================================== */

					        CASE

					            WHEN regtypecd IN ('NT', 'TP')
					            THEN 'REGULAR'

					            WHEN regtypecd = 'CO'
					            THEN 'COMPOSITION'

					            WHEN regtypecd = 'APLTD'
					            THEN 'TDS'

					            WHEN regtypecd = 'APLTC'
					            THEN 'TCS'

					            ELSE 'OTHER'

					        END AS registration_type

					    FROM analytical_dashboard.reg_details_ayush

					    WHERE 1=1

					      AND (? IS NULL OR zone = ?)

					      AND (? IS NULL OR unit = ?)

					      AND (? IS NULL OR circle = ?)

					)

					SELECT

					    COALESCE(circle, 'TOTAL') AS location,

					    /* =====================================
					       STATE JURISDICTION
					    ===================================== */

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'ACTIVE'
					             AND jurisdiction = 'STATE'
					             AND registration_type = 'REGULAR'
					            THEN 1
					        END
					    ) AS state_regular,

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'ACTIVE'
					             AND jurisdiction = 'STATE'
					             AND registration_type = 'COMPOSITION'
					            THEN 1
					        END
					    ) AS state_composition,

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'ACTIVE'
					             AND jurisdiction = 'STATE'
					             AND registration_type IN ('REGULAR', 'COMPOSITION')
					            THEN 1
					        END
					    ) AS state_total,

					    /* =====================================
					       CENTER JURISDICTION
					    ===================================== */

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'ACTIVE'
					             AND jurisdiction = 'CENTER'
					             AND registration_type = 'REGULAR'
					            THEN 1
					        END
					    ) AS center_regular,

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'ACTIVE'
					             AND jurisdiction = 'CENTER'
					             AND registration_type = 'COMPOSITION'
					            THEN 1
					        END
					    ) AS center_composition,

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'ACTIVE'
					             AND jurisdiction = 'CENTER'
					             AND registration_type IN ('REGULAR', 'COMPOSITION')
					            THEN 1
					        END
					    ) AS center_total,

					    /* =====================================
					       TOTAL ACTIVE
					    ===================================== */

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'ACTIVE'
					             AND registration_type = 'REGULAR'
					            THEN 1
					        END
					    ) AS total_regular,

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'ACTIVE'
					             AND registration_type = 'COMPOSITION'
					            THEN 1
					        END
					    ) AS total_composition,

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'ACTIVE'
					             AND registration_type IN ('REGULAR', 'COMPOSITION')
					            THEN 1
					        END
					    ) AS total_active_taxpayers,

					    /* =====================================
					       TDS / TCS
					    ===================================== */

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'ACTIVE'
					             AND registration_type = 'TDS'
					            THEN 1
					        END
					    ) AS tds,

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'ACTIVE'
					             AND registration_type = 'TCS'
					            THEN 1
					        END
					    ) AS tcs,

					    /* =====================================
					       CANCELLED
					    ===================================== */

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'CANCELLED'
					             AND jurisdiction = 'STATE'
					            THEN 1
					        END
					    ) AS cancelled_state,

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'CANCELLED'
					             AND jurisdiction = 'CENTER'
					            THEN 1
					        END
					    ) AS cancelled_center,

					    COUNT(
					        CASE
					            WHEN taxpayer_status = 'CANCELLED'
					            THEN 1
					        END
					    ) AS cancelled_total

					FROM taxpayer_data

					GROUP BY ROLLUP(circle)

					ORDER BY
					    CASE
					        WHEN circle IS NULL THEN 1
					        ELSE 0
					    END,
					    circle

					""";

			List<Map<String, Object>> result = jdbcTemplate.queryForList(

					sql,

					/* ZONE */
					zone != null && !zone.equalsIgnoreCase("All Zones") ? zone : null,

					zone != null && !zone.equalsIgnoreCase("All Zones") ? zone : null,

					/* UNIT */
					unit != null && !unit.equalsIgnoreCase("All Units") ? unit : null,

					unit != null && !unit.equalsIgnoreCase("All Units") ? unit : null,

					/* CIRCLE */
					circle != null && !circle.equalsIgnoreCase("All Circles") ? circle : null,

					circle != null && !circle.equalsIgnoreCase("All Circles") ? circle : null);

			return ResponseEntity.ok(result);

		} catch (Exception e) {

			e.printStackTrace();

			return ResponseEntity.badRequest().body("Error : " + e.getMessage());
		}
	}

	@GetMapping("/scrutinyReturns")
	public ResponseEntity<?> get_scrutiny_Returns(

			@RequestParam(required = false) String zone,

			@RequestParam(required = false) String unit,

			@RequestParam(required = false) String circle,

			@RequestParam(required = false) String financial_year,

			@RequestParam(required = false) String assesment_year

	) {

		try {

			System.out.println("inside scrutiny");

			StringBuilder query = new StringBuilder();

			query.append("""

					    SELECT
					        a.gstin,
					        a.crn,
					        a.dof,
					        a.nm,
					        a.status_desc,

					        a.igst_dtot,
					        a.cgst_dtot,
					        a.sgst_dtot,
					        a.cess_dtot,

					        (
					            COALESCE(a.igst_dtot, 0) +
					            COALESCE(a.cgst_dtot, 0) +
					            COALESCE(a.sgst_dtot, 0) +
					            COALESCE(a.cess_dtot, 0)
					        ) AS total,

					        r.trdnm,
					        r.circle,
					        r.unit,
					        r.zone,
					        r.appr_auth,

					        p.nm AS notice_officer,
					        p.nm AS drop_notice_officer

					    FROM gst_api_adjudication."SRNotice_ADJSR" a

					    INNER JOIN analytical_dashboard.reg_details_ayush r
					        ON a.gstin = r.gstin

					    LEFT JOIN gst_api_adjudication."SRProceeding_ADJSR" p
					        ON a.gstin = p.gstin

					    WHERE 1 = 1

					""");

			/*
			 * ========================= FILTER : ZONE =========================
			 */

			if (zone != null && !zone.trim().isEmpty() && !zone.equalsIgnoreCase("All Zones")) {

				query.append(" AND r.zone = '").append(zone.replace("'", "''")).append("' ");

			}

			/*
			 * ========================= FILTER : UNIT =========================
			 */

			if (unit != null && !unit.trim().isEmpty() && !unit.equalsIgnoreCase("All Units")) {

				query.append(" AND r.unit = '").append(unit.replace("'", "''")).append("' ");

			}

			/*
			 * ========================= FILTER : CIRCLE =========================
			 */

			if (circle != null && !circle.trim().isEmpty() && !circle.equalsIgnoreCase("All Circles")) {

				query.append(" AND r.circle = '").append(circle.replace("'", "''")).append("' ");

			}

			/*
			 * ========================= FILTER : FINANCIAL YEAR =========================
			 */
			/*
			 * if (financial_year != null && !financial_year.trim().isEmpty()) {
			 * 
			 * query.append(" AND a.financial_year = '") .append(financial_year.replace("'",
			 * "''")) .append("' ");
			 * 
			 * }
			 * 
			 * ========================= FILTER : ASSESSMENT YEAR =========================
			 * 
			 * if (assesment_year != null && !assesment_year.trim().isEmpty()) {
			 * 
			 * query.append(" AND a.assesment_year = '") .append(assesment_year.replace("'",
			 * "''")) .append("' ");
			 * 
			 * }
			 */
			/*
			 * ========================= ORDER BY =========================
			 */

			query.append(" ORDER BY a.dof DESC ");

			System.out.println("FINAL QUERY => ");
			System.out.println(query);

			List<Map<String, Object>> result = jdbcTemplate.queryForList(query.toString());

			return ResponseEntity.ok(result);

		} catch (Exception e) {

			e.printStackTrace();

			return ResponseEntity.badRequest().body("ERROR : " + e.getMessage());
		}
	}
	@GetMapping("/14ATaxpayerList")
	public ResponseEntity<?> get_14A_taxpayerlist(

	        @RequestParam(required = false) String zone,

	        @RequestParam(required = false) String unit,

	        @RequestParam(required = false) String circle,

	        @RequestParam(required = false) String status

	) {

	    try {

	        System.out.println("inside 14A_taxpayerlist");

	        StringBuilder query = new StringBuilder();

	        query.append("""

	            SELECT 
	                r.gstin,
	                r.trdnm,
	                r.zone,
	                r.unit,
	                r.circle,

	                r.appr_auth,

	                r.cobz,
	                r.ntbz,
	                r.authstatus,

	                m."Range",

	                r.apprvdt,
	                r.ppbz_address,

	                /* TAXPAYER TYPE */
	                CASE

	                    /* REGULAR */
	                    WHEN r.regtypecd IN ('NT', 'TP')
	                    THEN 'REGULAR'

	                    /* COMPOSITION */
	                    WHEN r.regtypecd = 'CO'
	                    THEN 'COMPOSITION'

	                    /* TDS */
	                    WHEN r.regtypecd = 'APLTD'
	                    THEN 'TDS'

	                    /* TCS */
	                    WHEN r.regtypecd = 'APLTC'
	                    THEN 'TCS'

	                    ELSE 'OTHERS'

	                END AS taxpayer_type,

	                /* GROSS TURNOVER */
	                SUM(
	                    COALESCE(g.txval_osup_det, 0::numeric) +
	                    COALESCE(g.txval_osup_zero, 0::numeric) +
	                    COALESCE(g.txval_osup_nil_exmp, 0::numeric) +
	                    COALESCE(g.txval_isup_rev, 0::numeric) +
	                    COALESCE(g.txval_osup_nongst, 0::numeric)
	                ) AS gross_turnover

	            FROM analytical_dashboard.reg_details_ayush r

	            INNER JOIN gst_api_registration.zone_unit_circle_mapping m
	                ON r.stjd = m.stjd

	            LEFT JOIN gst_api_gstr_3b."GSTR3B_RETURN" g
	                ON r.gstin = g.gstin

	            WHERE r."optCat" = 'O'

	        """);

	        /* =========================
	           ZONE FILTER
	        ========================= */

	        if (zone != null &&
	            !zone.trim().isEmpty() &&
	            !zone.equalsIgnoreCase("All Zones")) {

	            query.append(" AND r.zone = '")
	                 .append(zone.replace("'", "''"))
	                 .append("' ");

	        }

	        /* =========================
	           UNIT FILTER
	        ========================= */

	        if (unit != null &&
	            !unit.trim().isEmpty() &&
	            !unit.equalsIgnoreCase("All Units")) {

	            query.append(" AND r.unit = '")
	                 .append(unit.replace("'", "''"))
	                 .append("' ");

	        }

	        /* =========================
	           CIRCLE FILTER
	        ========================= */

	        if (circle != null &&
	            !circle.trim().isEmpty() &&
	            !circle.equalsIgnoreCase("All Circles")) {

	            query.append(" AND r.circle = '")
	                 .append(circle.replace("'", "''"))
	                 .append("' ");

	        }

	        /* =========================
	           STATUS FILTER
	        ========================= */

	        if (status != null &&
	            !status.trim().isEmpty() &&
	            !status.equalsIgnoreCase("All")) {

	            query.append(" AND r.authstatus = '")
	                 .append(status.replace("'", "''"))
	                 .append("' ");

	        }

	        /* =========================
	           GROUP BY
	        ========================= */

	        query.append("""

	            GROUP BY
	                r.gstin,
	                r.trdnm,
	                r.zone,
	                r.unit,
	                r.circle,
	                r.appr_auth,
	                r.regtypecd,
	                r.cobz,
	                r.ntbz,
	                r.authstatus,
	                m."Range",
	                r.apprvdt,
	                r.ppbz_address

	            ORDER BY
	                r.zone,
	                r.unit,
	                r.circle,
	                r.gstin

	        """);

	        System.out.println("FINAL QUERY => " + query);

	        List<Map<String, Object>> result =
	                jdbcTemplate.queryForList(query.toString());

	        return ResponseEntity.ok(result);

	    } catch (Exception e) {

	        e.printStackTrace();

	        return ResponseEntity
	                .badRequest()
	                .body("ERROR : " + e.getMessage());
	    }
	}

	public ResponseEntity<?> Apportionment_gstwise(@RequestParam(required = false) String gstin,

			@RequestParam(required = false) String year

	) {
		System.out.println("Apportionment_gstwise");
		return null;

	}


	public ResponseEntity<?> Apportionment_unitwise(@RequestParam(required = false) String gstin,

			@RequestParam(required = false) String year

	) {
		System.out.println("Apportionment_unitwise");
		return null;

	}
	public ResponseEntity<?> Apportionment_circlewise(@RequestParam(required = false) String gstin,

			@RequestParam(required = false) String year

	) {
		System.out.println("Apportionment_circlewise");
		return null;

	}
	public ResponseEntity<?> Gstr_2b_details(@RequestParam(required = false) String gstin,

			@RequestParam(required = false) String year

	) {
		System.out.println("inside Gstr_2b_details");
		return null;

	}
}