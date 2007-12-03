package org.hivedb.util.database.test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Random;

import org.hivedb.hibernate.annotations.EntityId;
import org.hivedb.hibernate.annotations.Index;
import org.hivedb.hibernate.annotations.PartitionIndex;
import org.hivedb.hibernate.annotations.Resource;
import org.hivedb.util.GenerateInstance;
import org.hivedb.util.GeneratePrimitiveCollection;
import org.hivedb.util.HiveUtils;
import org.hivedb.util.functional.NumberIterator;
import org.hivedb.util.functional.Transform;
import org.hivedb.util.functional.Unary;

@Deprecated
@Resource(name="WeatherReport")
public class WeatherReportImpl implements WeatherReport {
	private static final String[] continents = 
		new String[]{"North America","South America", "Asia", "Europe","Africa","Australia","Antarctica"};
    private Integer reportId;
    private String continent;
    private BigDecimal latitude,longitude;
    private int temperature;
    private Date reportTime;
	public static final String REPORT_ID = "reportId";
	public static final String TEMPERATURE = "temperature";
	public static final String CONTINENT = "continent";
	
    @PartitionIndex(name=WeatherReport.CONTINENT)
	public String getContinent() {
		return continent;
	}
	public void setContinent(String continent) {
		this.continent = continent;
	}
	public BigDecimal getLatitude() {
		return latitude;
	}
	public void setLatitude(BigDecimal latitude) {
		this.latitude = latitude;
	}
	public BigDecimal getLongitude() {
		return longitude;
	}
	public void setLongitude(BigDecimal longitude) {
		this.longitude = longitude;
	}
	@EntityId
	public Integer getReportId() {
		return reportId;
	}
	public void setReportId(Integer reportId) {
		this.reportId = reportId;
	}
	
	public Date getReportTime() {
		return reportTime;
	}
	public void setReportTime(Date reportTime) {
		this.reportTime = reportTime;
	}
	@Index
	public int getTemperature() {
		return temperature;
	}
	public void setTemperature(int temperature) {
		this.temperature = temperature;
	}
	
	@Index
	public Collection<Integer> getWeeklyTemperatures() {
		return new GeneratePrimitiveCollection<Integer>(Integer.class,7).generate();
	}
	
	@Override
	public boolean equals(Object obj) {
		return this.hashCode() == obj.hashCode();
	}
	@Override
	public int hashCode() {
		return HiveUtils.makeHashCode(new Object[]{getContinent(),getLatitude(), getLongitude(), getReportId(), getReportTime()});
	}
	public static WeatherReportImpl generate() {
		Random r = new Random(System.currentTimeMillis());
		WeatherReportImpl weatherReport = new WeatherReportImpl();
		weatherReport.setContinent(continents[r.nextInt(continents.length)]);
		weatherReport.setLatitude(new BigDecimal(360*r.nextDouble()));
		weatherReport.setLongitude(new BigDecimal(360*r.nextDouble()));
		weatherReport.setReportId(r.nextInt());
		weatherReport.setReportTime(new Date(System.currentTimeMillis()));
		weatherReport.setTemperature((int) Math.rint(Math.random() * 100));
		return weatherReport;
	}
	public void setWeeklyTemperatures(Collection<Integer> values) {
		
	}
}
