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
    private static final String DETAIL_URL = "https://restapi.amap.com/v3/place/detail";

    /**
     * 地址转经纬度（自动提取城市）
     */
    public double[] geocode(String address) {
        String city = extractCity(address);
        return geocode(address, city);
    }

    /**
     * 地址转经纬度（指定城市，提高精度）
     */
    public double[] geocode(String address, String city) {
        try {
            String encodedAddr = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String url = GEO_URL + "?key=" + apiKey + "&address=" + encodedAddr + "&output=JSON";
            if (city != null && !city.isEmpty()) {
                url += "&city=" + URLEncoder.encode(city, StandardCharsets.UTF_8);
            }
            String resp = HttpUtil.get(url);
            JSONObject json = JSONUtil.parseObj(resp);
            JSONArray geocodes = json.getJSONArray("geocodes");
            if (geocodes != null && !geocodes.isEmpty()) {
                String location = geocodes.getJSONObject(0).getStr("location");
                String[] parts = location.split(",");
                return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
            }
        } catch (Exception e) {
            log.error("地理编码失败: {} (city={})", address, city, e);
        }
        return null;
    }

    /**
     * 从地址字符串中提取城市名
     * 如 "杭州西湖" → "杭州", "北京朝阳区" → "北京", "上海浦东新区" → "上海"
     */
    public static String extractCity(String location) {
        if (location == null || location.isEmpty()) return "";
        String[] cities = {
                "北京", "上海", "广州", "深圳", "杭州", "成都", "重庆", "武汉", "南京", "西安",
                "苏州", "天津", "长沙", "郑州", "青岛", "厦门", "昆明", "合肥", "福州", "济南",
                "哈尔滨", "沈阳", "大连", "宁波", "无锡", "佛山", "东莞", "珠海", "贵阳", "南宁",
                "太原", "石家庄", "南昌", "兰州", "海口", "呼和浩特", "银川", "西宁", "拉萨", "乌鲁木齐"
        };
        for (String city : cities) {
            if (location.startsWith(city)) return city;
        }
        return "";
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
                r.setId(poi.getStr("id"));
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
        private String id;
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

    /**
     * POI 详情（评分、评价、照片、营业信息）
     */
    @lombok.Data
    public static class PoiDetail {
        private String name;
        private String address;
        private String rating;           // 评分
        private String cost;             // 人均消费
        private String businessHours;    // 营业时间
        private List<String> photos;     // 照片 URL 列表
        private List<String> reviews;    // 评价摘要列表
    }

    /**
     * 查询 POI 详情（评分、评价、照片）
     * @param poiId POI 的 id（从 aroundSearch/textSearch 结果中获取）
     */
    public PoiDetail placeDetail(String poiId) {
        PoiDetail detail = new PoiDetail();
        detail.setPhotos(new ArrayList<>());
        detail.setReviews(new ArrayList<>());
        try {
            // v3 API: id=xxx, 返回包含 photos/biz_ext/rating
            String url = DETAIL_URL + "?key=" + apiKey + "&id=" + poiId + "&output=JSON";
            String resp = HttpUtil.get(url);
            JSONObject json = JSONUtil.parseObj(resp);
            JSONArray pois = json.getJSONArray("pois");
            if (pois == null || pois.isEmpty()) {
                log.warn("POI 详情为空: {}", poiId);
                return detail;
            }

            JSONObject poi = pois.getJSONObject(0);
            detail.setName(poi.getStr("name"));
            detail.setAddress(poi.getStr("address"));

            // 评分和人均在 biz_ext 中
            JSONObject bizExt = poi.getJSONObject("biz_ext");
            if (bizExt != null) {
                detail.setRating(bizExt.getStr("rating", ""));
                detail.setCost(bizExt.getStr("cost", ""));
                detail.setBusinessHours(bizExt.getStr("open_time", ""));
            }

            // 照片
            JSONArray photos = poi.getJSONArray("photos");
            if (photos != null) {
                List<String> photoUrls = new ArrayList<>();
                for (int i = 0; i < Math.min(photos.size(), 5); i++) {
                    JSONObject photo = photos.getJSONObject(i);
                    String url2 = photo.getStr("url");
                    if (url2 != null && !url2.isEmpty()) photoUrls.add(url2);
                }
                detail.setPhotos(photoUrls);
            }

            log.info("POI 详情 [{}]: rating={}, cost={}, photos={}",
                    detail.getName(), detail.getRating(), detail.getCost(), detail.getPhotos().size());
        } catch (Exception e) {
            log.error("POI 详情查询失败: {}", poiId, e);
        }
        return detail;
    }
}
