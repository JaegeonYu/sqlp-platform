package dev.sqlp.judge;

/**
 * 튜닝 문제 채점 결과. 판정 순서는 결과 해시 → Buffers 기준치 → 실행계획 조건이다.
 */
public enum Verdict {

	/** 결과가 같고 Buffers·실행계획 조건을 모두 만족 */
	AC,

	/** 결과가 원본 SQL과 다름 */
	WA,

	/** 결과는 같지만 Buffers가 기준치를 넘음 */
	PERF,

	/** 결과·Buffers는 만족하지만 실행계획 조건 위반 */
	PLAN,

	/** 제한 시간 초과 */
	TLE,

	/** 실행 중 ORA- 오류 */
	RE,

	/** 허용되지 않은 문장(DML, DDL 등) */
	REJECTED

}
