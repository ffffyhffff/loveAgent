package com.aichat.app.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 高德地图 REST API 封装
 *
 * 使用 v5 API，中文关键词需要 URL 编码
 */
@Component
@Slf4j
public class AmapTools {

    @Value("${amap.api-key:}")
    private String apiKey;

    private static final String GEO_URL = "https://restapi.amap.com/v3/geocode/geo";
    private static final String AROUND_URL = "https://restapi.amap.com/v5/place/around";
    private static final String TEXT_URL = "https://restapi.amap.com/v5/place/text";
    private static final String WALKING_URL = "https://restapi.amap.com/v5/direction/walking";

    /**
     * 地址转经纬度
     */
    public double[] geocode(String address) {
        try {
            String encodedAddr = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String resp = HttpUtil.get(GEO_URL + "?key=" + apiKey + "&address=" + encodedAddr + "&output=JSON");
            JSONObject json = JSONUtil.parseObj(resp);
            JSONArray geocodes = json.getJSONArray("geocodes");
            if (geocodes != null && !geocodes.isEmpty()) {
                String location = geocodes.getJSONObject(0).getStr("location");
                String[] parts = location.split(",");
                return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
            }
        } catch (Exception e) {
            log.error("地理编码失败: {}", address, e);
        }
        return null;
    }

    /**
     * 附近搜索 POI
     * @param lon 经度
     * @param lat 纬度
     * @param keywords 关键词（中文需编码）
     * @param radius 搜索半径（米）
     */
    public List<PoiResult> aroundSearch(double lon, double lat, String keywords, int radius) {
        List<PoiResult> results = new ArrayList<>();
        try {
            String encodedKw = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
            String url = AROUND_URL + "?key=" + apiKey
                    + "&location=" + lon + "," + lat
                    + "&keywords=" + encodedKw
                    + "&radius=" + radius
                    + "&page_size=5";
            String resp = HttpUtil.get(url);
            results = parsePois(resp);
            log.info("附近搜索 [{}] → {} 条结果", keywords, results.size());
        } catch (Exception e) {
            log.error("附近搜索失败", e);
        }
        return results;
    }

    /**
     * 关键词搜索 POI
     */
    public List<PoiResult> textSearch(String keywords, String city) {
        List<PoiResult> results = new ArrayList<>();
        try {
            String encodedKw = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
            String url = TEXT_URL + "?key=" + apiKey
                    + "&keywords=" + encodedKw
                    + "&city=" + (city != null ? URLEncoder.encode(city, StandardCharsets.UTF_8) : "")
                    + "&page_size=5";
            String resp = HttpUtil.get(url);
            results = parsePois(resp);
            log.info("关键词搜索 [{}] → {} 条结果", keywords, results.size());
        } catch (Exception e) {
            log.error("关键词搜索失败", e);
        }
        return results;
    }

    private List<PoiResult> parsePois(String resp) {
        List<PoiResult> results = new ArrayList<>();
        try {
            JSONObject json = JSONUtil.parseObj(resp);
            JSONArray pois = json.getJSONArray("pois");
            if (pois == null) return results;
            for (int i = 0; i < pois.size(); i++) {
                JSONObject poi = pois.getJSONObject(i);
                PoiResult r = new PoiResult();
                r.setName(poi.getStr("name"));
                r.setAddress(poi.getStr("address"));
                r.setDistance(poi.getStr("distance"));
                String loc = poi.getStr("location");
                if (loc != null) {
                    String[] parts = loc.split(",");
                    r.setLongitude(Double.parseDouble(parts[0]));
                    r.setLatitude(Double.parseDouble(parts[1]));
                }
                results.add(r);
            }
        } catch (Exception e) {
            log.error("解析 POI 失败", e);
        }
        return results;
    }

    /**
     * 步行路线规划
     */
    public RouteResult walkingRoute(double[] origin, double[] destination) {
        try {
            String url = WALKING_URL + "?key=" + apiKey
                    + "&origin=" + origin[0] + "," + origin[1]
                    + "&destination=" + destination[0] + "," + destination[1];
            String resp = HttpUtil.get(url);
            JSONObject json = JSONUtil.parseObj(resp);
            JSONArray paths = json.getJSONArray("paths");
            if (paths != null && !paths.isEmpty()) {
                JSONObject path = paths.getJSONObject(0);
                RouteResult result = new RouteResult();
                result.setDistance(path.getStr("distance") + "米");
                int secs = path.getInt("duration");
                result.setDuration((secs / 60) + "分钟");
                return result;
            }
        } catch (Exception e) {
            log.error("步行路线规划失败", e);
        }
        return new RouteResult();
    }

    @lombok.Data
    public static class PoiResult {
        private String name;
        private String address;
        private String distance;
        private double longitude;
        private double latitude;
    }

    @lombok.Data
    public static class RouteResult {
        private String distance;
        private String duration;
    }
}
