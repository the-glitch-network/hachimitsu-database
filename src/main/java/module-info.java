// Created 2021-11-07T05:15:41

/**
 * @author KJP12
 * @since ${version}
 **/
module net.kjp12.database {
	requires java.sql;
	requires org.objectweb.asm;

	exports net.kjp12.hachimitsu.database.api;
	exports net.kjp12.hachimitsu.database.api.annotation;
}