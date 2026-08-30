package com.metalurgica.estoque.domain.enums;

/**
 * Perfis de acesso, desenhados sobre como a metalúrgica realmente se organiza.
 * <p>
 * O corte que importa é <b>quem pode ver a margem de lucro</b>. Preço de venda e
 * margem são informação dos donos e de quem monta o orçamento; não do restante
 * da oficina.
 */
public enum Role {

    /** Os donos. Acesso a tudo, incluindo criar e remover usuários. */
    ADMIN,

    /**
     * Quem cuida da papelada e monta orçamento. Precisa da margem para trabalhar,
     * mas não administra contas de acesso — foi o próprio dono quem separou
     * "os quatro sócios" de "o funcionário do escritório".
     */
    ESCRITORIO,

    /**
     * Chão de fábrica. Registra entrada e saída de material e o corte executado,
     * sem enxergar preço de venda nem margem.
     */
    OPERADOR
}
