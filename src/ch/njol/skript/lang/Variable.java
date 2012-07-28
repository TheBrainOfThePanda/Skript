/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2011, 2012 Peter Güttinger
 * 
 */

package ch.njol.skript.lang;

import java.lang.reflect.Array;

import org.bukkit.event.Event;

import ch.njol.skript.Skript;
import ch.njol.skript.api.Changer.ChangeMode;
import ch.njol.skript.api.Changer.ChangerUtils;
import ch.njol.skript.api.Condition;
import ch.njol.skript.api.Converter;
import ch.njol.skript.api.Converter.ConverterUtils;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.StringMode;
import ch.njol.skript.util.VariableString;
import ch.njol.util.Checker;
import ch.njol.util.StringUtils;
import ch.njol.util.Validate;
import ch.njol.util.iterator.NonNullIterator;

/**
 * @author Peter Güttinger
 * 
 */
public class Variable<T> implements Expression<T> {
	
	private final VariableString name;
	private final Class<T> type;
	private final T[] zero, one;
	
	private final Variable<?> source;
	
	private Variable(final VariableString name, final Class<T> type, final Variable<?> source) {
		Validate.notNull(name, type);
		if (name.getMode() != StringMode.VARIABLE_NAME) // not setMode as angle brackets are not allowed in variable names
			throw new IllegalArgumentException("'name' must be a VARIABLE_NAME string");
		this.name = name;
		this.type = type;
		zero = (T[]) Array.newInstance(type, 0);
		one = (T[]) Array.newInstance(type, 1);
		
		this.source = source;
	}
	
	public Variable(final VariableString name, final Class<T> type) {
		this(name, type, null);
	}
	
	@Override
	public boolean init(final Expression<?>[] exprs, final int matchedPattern, final ParseResult parseResult) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean isSingle() {
		return true;
	}
	
	@Override
	public Class<? extends T> getReturnType() {
		return type;
	}
	
	@Override
	public String toString(final Event e, final boolean debug) {
		return "{" + StringUtils.substring(name.toString(e, debug), 1, -1) + "}" + (debug ? "(as " + type.getName() + ")" : "");
	}
	
	@Override
	public String toString() {
		return toString(null, false);
	}
	
	@Override
	public <R> Expression<? extends R> getConvertedExpression(final Class<R> to) {
		return new Variable<R>(name, to, this);
	}
	
	protected T get(final Event e) {
		return Skript.convert(Skript.getVariable(name.toString(e).toLowerCase()), type);
	}
	
	private final void set(final Event e, final Object value) {
		Skript.setVariable(name.toString(e).toLowerCase(), value);
	}
	
	@Override
	public Class<?> acceptChange(final ChangeMode mode) {
		return Object.class;
	}
	
	@Override
	public void change(final Event e, final Object delta, final ChangeMode mode) throws UnsupportedOperationException {
		if (mode == ChangeMode.CLEAR) {
			set(e, null);
		} else if (mode == ChangeMode.SET) {
			final ClassInfo<?> ci = Skript.getSuperClassInfo(delta.getClass());
			if (ci != null && ci.getSerializeAs() != null) {
				set(e, Skript.convert(delta, ci.getSerializeAs()));
			} else {
				set(e, delta);
			}
		} else {
			final Object o = one[0] = get(e);
			if (o == null)
				return;
			final ClassInfo<?> ci = Skript.getSuperClassInfo(o.getClass());
			if (ci.getChanger() != null && ci.getChanger().acceptChange(mode) != null && ci.getChanger().acceptChange(mode).isAssignableFrom(delta.getClass())) {
				ChangerUtils.change(ci.getChanger(), one, delta, mode);
			}
			if (o instanceof Number && delta instanceof Number) {
				final int i = mode == ChangeMode.ADD ? 1 : -1;
				set(e, ((Number) o).doubleValue() + i * ((Number) delta).doubleValue());
			}
		}
	}
	
	@Override
	public T getSingle(final Event e) {
		return get(e);
	}
	
	@Override
	public T[] getArray(final Event e) {
		return getAll(e);
	}
	
	@Override
	public T[] getAll(final Event e) {
		one[0] = get(e);
		if (one[0] == null)
			return zero;
		return one;
	}
	
	@Override
	public <V> V getSingle(final Event e, final Converter<? super T, ? extends V> converter) {
		final T t = get(e);
		if (t == null)
			return null;
		return converter.convert(t);
	}
	
	@Override
	public <V> V[] getArray(final Event e, final Class<V> to, final Converter<? super T, ? extends V> converter) {
		return ConverterUtils.convert(getArray(e), converter, to);
	}
	
	@Override
	public boolean check(final Event e, final Checker<? super T> c, final Condition cond) {
		return SimpleExpression.check(getArray(e), c, cond.isNegated(), getAnd());
	}
	
	@Override
	public boolean check(final Event e, final Checker<? super T> c) {
		return SimpleExpression.check(getArray(e), c, false, getAnd());
	}
	
	@Override
	public boolean getAnd() {
		return false;
	}
	
	@Override
	public boolean setTime(final int time) {
		return false;
	}
	
	@Override
	public int getTime() {
		return 0;
	}
	
	@Override
	public boolean isDefault() {
		return false;
	}
	
	@Override
	public boolean canLoop() {
		return false;
	}
	
	@Override
	public NonNullIterator<T> iterator(final Event e) {
		return null;
	}
	
	@Override
	public boolean isLoopOf(final String s) {
		return false;
	}
	
	@Override
	public Expression<?> getSource() {
		return source == null ? this : source;
	}
	
}
